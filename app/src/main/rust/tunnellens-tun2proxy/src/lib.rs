//! TunnelLens-owned, process-safe FFI boundary for the vendored tun2proxy core.
//!
//! The upstream CLI and JNI entry points are intentionally not used. This
//! boundary accepts typed fields, catches Rust panics before they can unwind
//! through C, owns the supplied TUN descriptor on every start attempt, and
//! stops through a cancellation token. It never requests process termination.

use std::{
    ffi::c_char,
    net::{IpAddr, SocketAddr},
    panic::{AssertUnwindSafe, catch_unwind},
    ptr,
    sync::{Condvar, LazyLock, Mutex, MutexGuard},
    time::Duration,
};
use tun2proxy::{ArgDns, ArgProxy, ArgVerbosity, Args, CancellationToken, ProxyType, UserKey};

#[cfg(unix)]
use std::os::fd::{FromRawFd, IntoRawFd, OwnedFd};
#[cfg(unix)]
use tun2proxy::{
    TrafficStatus, general_run_async, reset_traffic_status, tun2proxy_set_traffic_status_callback,
};

pub const VERSION: &str = "tun2proxy/0.8.2+tunnellens.1";
pub const ABI_VERSION: u32 = 1;

const VERSION_C: &[u8] = b"tun2proxy/0.8.2+tunnellens.1\0";
const RESULT_OK: i32 = 0;
const RESULT_TIMEOUT: i32 = 1;
const ERROR_INVALID_CONFIG: i32 = -10;
const ERROR_BUSY: i32 = -11;
#[cfg(not(unix))]
const ERROR_UNSUPPORTED_PLATFORM: i32 = -12;
#[cfg(unix)]
const ERROR_THREAD_START: i32 = -13;
#[cfg(unix)]
const ERROR_RUNTIME: i32 = -14;
#[cfg(unix)]
const ERROR_TUNNEL: i32 = -15;
const ERROR_PANIC: i32 = -16;

const PROXY_SOCKS5: u32 = 1;
const PROXY_HTTP: u32 = 2;
const DNS_DIRECT: u32 = 0;
const DNS_OVER_TCP: u32 = 1;
const DNS_VIRTUAL: u32 = 2;

const MAX_ADDRESS_BYTES: usize = 64;
const MAX_CREDENTIAL_BYTES: usize = 255;
const MIN_MTU: u16 = 576;
const MAX_MTU: u16 = 9_000;
const MAX_SESSIONS: usize = 10_000;

/// Versioned configuration for the stable C ABI.
///
/// `tun_fd` ownership transfers to Rust on every call when it is non-negative,
/// including validation, busy, thread-start, and runtime failure paths.
#[repr(C)]
pub struct TunnelLensTun2ProxyConfig {
    pub struct_size: u32,
    pub tun_fd: i32,
    pub proxy_type: u32,
    pub proxy_address: *const u8,
    pub proxy_address_len: usize,
    pub proxy_port: u16,
    pub mtu: u16,
    pub username: *const u8,
    pub username_len: usize,
    pub password: *const u8,
    pub password_len: usize,
    pub dns_strategy: u32,
    pub dns_address: *const u8,
    pub dns_address_len: usize,
    pub ipv6_enabled: u8,
    pub reserved: [u8; 7],
    pub tcp_timeout_seconds: u64,
    pub udp_timeout_seconds: u64,
    pub max_sessions: u32,
}

struct ParsedConfig {
    args: Args,
    #[cfg_attr(not(unix), allow(dead_code))]
    mtu: u16,
}

#[cfg_attr(not(unix), allow(dead_code))]
#[derive(Clone, Copy)]
enum Phase {
    Idle,
    Running,
    Exited(i32),
}

struct SessionState {
    phase: Phase,
    #[cfg_attr(not(unix), allow(dead_code))]
    generation: u64,
    cancellation: Option<CancellationToken>,
}

static SESSION: LazyLock<(Mutex<SessionState>, Condvar)> = LazyLock::new(|| {
    (
        Mutex::new(SessionState {
            phase: Phase::Idle,
            generation: 0,
            cancellation: None,
        }),
        Condvar::new(),
    )
});

static UPLOADED_BYTES: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
static DOWNLOADED_BYTES: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);

fn lock_session() -> MutexGuard<'static, SessionState> {
    SESSION
        .0
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner())
}

#[cfg(unix)]
unsafe extern "C" fn update_traffic(status: *const TrafficStatus, _context: *mut std::ffi::c_void) {
    let Some(status) = (unsafe { status.as_ref() }) else {
        return;
    };
    UPLOADED_BYTES.store(status.tx, std::sync::atomic::Ordering::Relaxed);
    DOWNLOADED_BYTES.store(status.rx, std::sync::atomic::Ordering::Relaxed);
}

unsafe fn read_utf8(
    ptr: *const u8,
    len: usize,
    max_len: usize,
    allow_empty: bool,
) -> Result<String, i32> {
    if len == 0 {
        return allow_empty.then(String::new).ok_or(ERROR_INVALID_CONFIG);
    }
    if ptr.is_null() || len > max_len {
        return Err(ERROR_INVALID_CONFIG);
    }
    let bytes = unsafe { std::slice::from_raw_parts(ptr, len) };
    let value = std::str::from_utf8(bytes).map_err(|_| ERROR_INVALID_CONFIG)?;
    if value.as_bytes().contains(&0) {
        return Err(ERROR_INVALID_CONFIG);
    }
    Ok(value.to_owned())
}

unsafe fn parse_config(config: &TunnelLensTun2ProxyConfig) -> Result<ParsedConfig, i32> {
    if config.struct_size as usize != std::mem::size_of::<TunnelLensTun2ProxyConfig>()
        || config.proxy_port == 0
        || !(MIN_MTU..=MAX_MTU).contains(&config.mtu)
        || config.tcp_timeout_seconds == 0
        || config.udp_timeout_seconds == 0
        || config.max_sessions == 0
        || config.max_sessions as usize > MAX_SESSIONS
        || config.ipv6_enabled > 1
        || config.reserved != [0; 7]
    {
        return Err(ERROR_INVALID_CONFIG);
    }

    let proxy_ip: IpAddr = unsafe {
        read_utf8(
            config.proxy_address,
            config.proxy_address_len,
            MAX_ADDRESS_BYTES,
            false,
        )
    }?
    .parse()
    .map_err(|_| ERROR_INVALID_CONFIG)?;
    let dns_ip: IpAddr = unsafe {
        read_utf8(
            config.dns_address,
            config.dns_address_len,
            MAX_ADDRESS_BYTES,
            false,
        )
    }?
    .parse()
    .map_err(|_| ERROR_INVALID_CONFIG)?;
    let username = unsafe {
        read_utf8(
            config.username,
            config.username_len,
            MAX_CREDENTIAL_BYTES,
            true,
        )
    }?;
    let password = unsafe {
        read_utf8(
            config.password,
            config.password_len,
            MAX_CREDENTIAL_BYTES,
            true,
        )
    }?;

    let proxy_type = match config.proxy_type {
        PROXY_SOCKS5 => ProxyType::Socks5,
        PROXY_HTTP => ProxyType::Http,
        _ => return Err(ERROR_INVALID_CONFIG),
    };
    let dns = match config.dns_strategy {
        DNS_DIRECT => ArgDns::Direct,
        DNS_OVER_TCP => ArgDns::OverTcp,
        DNS_VIRTUAL => ArgDns::Virtual,
        _ => return Err(ERROR_INVALID_CONFIG),
    };

    let credentials = if username.is_empty() && password.is_empty() {
        None
    } else {
        Some(UserKey::new(username, password))
    };
    let mut args = Args::default();
    args.proxy(ArgProxy {
        proxy_type,
        addr: SocketAddr::new(proxy_ip, config.proxy_port),
        credentials,
    })
    .dns(dns)
    .dns_addr(dns_ip)
    .ipv6_enabled(config.ipv6_enabled == 1)
    .setup(false)
    .verbosity(ArgVerbosity::Warn);
    args.mtu = config.mtu;
    args.tcp_timeout = config.tcp_timeout_seconds;
    args.udp_timeout = config.udp_timeout_seconds;
    args.max_sessions = config.max_sessions as usize;
    args.exit_on_fatal_error = false;

    Ok(ParsedConfig {
        args,
        mtu: config.mtu,
    })
}

#[cfg(unix)]
fn finish_session(generation: u64, exit_code: i32) {
    unsafe { tun2proxy_set_traffic_status_callback(0, None, ptr::null_mut()) };
    let mut state = lock_session();
    if state.generation == generation {
        state.phase = Phase::Exited(exit_code);
        state.cancellation = None;
        SESSION.1.notify_all();
    }
}

#[cfg(unix)]
fn run_session(
    owned_tun_fd: OwnedFd,
    mut parsed: ParsedConfig,
    cancellation: CancellationToken,
    generation: u64,
) {
    let exit_code = catch_unwind(AssertUnwindSafe(|| {
        let runtime = match tokio_runtime() {
            Ok(runtime) => runtime,
            Err(()) => return ERROR_RUNTIME,
        };
        let raw_fd = owned_tun_fd.into_raw_fd();
        parsed.args.tun_fd(Some(raw_fd)).close_fd_on_drop(true);
        match runtime.block_on(general_run_async(
            parsed.args,
            parsed.mtu,
            false,
            cancellation,
        )) {
            Ok(_) => RESULT_OK,
            Err(_) => ERROR_TUNNEL,
        }
    }))
    .unwrap_or(ERROR_PANIC);
    finish_session(generation, exit_code);
}

#[cfg(unix)]
fn tokio_runtime() -> Result<tokio::runtime::Runtime, ()> {
    tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .map_err(|_| ())
}

#[cfg(unix)]
unsafe fn start_inner(config: *const TunnelLensTun2ProxyConfig) -> i32 {
    let Some(config) = (unsafe { config.as_ref() }) else {
        return ERROR_INVALID_CONFIG;
    };
    if config.tun_fd < 0 {
        return ERROR_INVALID_CONFIG;
    }

    // Establish ownership before any operation that can fail or panic.
    let owned_tun_fd = unsafe { OwnedFd::from_raw_fd(config.tun_fd) };
    let parsed = match unsafe { parse_config(config) } {
        Ok(parsed) => parsed,
        Err(code) => return code,
    };

    let cancellation = CancellationToken::new();
    let mut state = lock_session();
    if matches!(state.phase, Phase::Running) {
        return ERROR_BUSY;
    }
    state.generation = state.generation.wrapping_add(1);
    let generation = state.generation;
    state.phase = Phase::Running;
    state.cancellation = Some(cancellation.clone());

    UPLOADED_BYTES.store(0, std::sync::atomic::Ordering::Relaxed);
    DOWNLOADED_BYTES.store(0, std::sync::atomic::Ordering::Relaxed);
    reset_traffic_status();
    unsafe { tun2proxy_set_traffic_status_callback(1, Some(update_traffic), ptr::null_mut()) };

    let spawn_result = std::thread::Builder::new()
        .name("tunnellens-tun2proxy".to_owned())
        .spawn(move || run_session(owned_tun_fd, parsed, cancellation, generation));
    if spawn_result.is_err() {
        state.phase = Phase::Exited(ERROR_THREAD_START);
        state.cancellation = None;
        unsafe { tun2proxy_set_traffic_status_callback(0, None, ptr::null_mut()) };
        SESSION.1.notify_all();
        return ERROR_THREAD_START;
    }
    RESULT_OK
}

#[cfg(not(unix))]
unsafe fn start_inner(_config: *const TunnelLensTun2ProxyConfig) -> i32 {
    ERROR_UNSUPPORTED_PLATFORM
}

#[unsafe(no_mangle)]
pub extern "C" fn tunnellens_tun2proxy_abi_version() -> u32 {
    ABI_VERSION
}

#[unsafe(no_mangle)]
pub extern "C" fn tunnellens_tun2proxy_version() -> *const c_char {
    VERSION_C.as_ptr().cast()
}

/// # Safety
///
/// `config` and all non-empty buffers it references must remain valid for this
/// call. Rust copies every field before returning.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn tunnellens_tun2proxy_start(
    config: *const TunnelLensTun2ProxyConfig,
) -> i32 {
    catch_unwind(AssertUnwindSafe(|| unsafe { start_inner(config) })).unwrap_or(ERROR_PANIC)
}

#[unsafe(no_mangle)]
pub extern "C" fn tunnellens_tun2proxy_request_stop() -> i32 {
    catch_unwind(AssertUnwindSafe(|| {
        if let Some(cancellation) = lock_session().cancellation.clone() {
            cancellation.cancel();
        }
        RESULT_OK
    }))
    .unwrap_or(ERROR_PANIC)
}

#[unsafe(no_mangle)]
pub extern "C" fn tunnellens_tun2proxy_wait_for_exit(timeout_millis: u32) -> i32 {
    catch_unwind(AssertUnwindSafe(|| {
        let state = lock_session();
        if !matches!(state.phase, Phase::Running) {
            return RESULT_OK;
        }
        let (state, wait) = SESSION
            .1
            .wait_timeout_while(
                state,
                Duration::from_millis(timeout_millis.into()),
                |state| matches!(state.phase, Phase::Running),
            )
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        if matches!(state.phase, Phase::Running) && wait.timed_out() {
            RESULT_TIMEOUT
        } else {
            RESULT_OK
        }
    }))
    .unwrap_or(ERROR_PANIC)
}

#[unsafe(no_mangle)]
pub extern "C" fn tunnellens_tun2proxy_is_running() -> i32 {
    catch_unwind(AssertUnwindSafe(|| {
        i32::from(matches!(lock_session().phase, Phase::Running))
    }))
    .unwrap_or(ERROR_PANIC)
}

#[unsafe(no_mangle)]
pub extern "C" fn tunnellens_tun2proxy_last_exit_code() -> i32 {
    catch_unwind(AssertUnwindSafe(|| match lock_session().phase {
        Phase::Idle => RESULT_OK,
        Phase::Running => ERROR_BUSY,
        Phase::Exited(code) => code,
    }))
    .unwrap_or(ERROR_PANIC)
}

/// # Safety
///
/// Either output pointer may be null. Non-null pointers must be writable.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn tunnellens_tun2proxy_stats(uploaded: *mut u64, downloaded: *mut u64) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        if let Some(uploaded) = unsafe { uploaded.as_mut() } {
            *uploaded = UPLOADED_BYTES.load(std::sync::atomic::Ordering::Relaxed);
        }
        if let Some(downloaded) = unsafe { downloaded.as_mut() } {
            *downloaded = DOWNLOADED_BYTES.load(std::sync::atomic::Ordering::Relaxed);
        }
    }));
}

#[cfg(test)]
mod tests {
    use super::*;

    fn bytes(value: &str) -> (*const u8, usize) {
        (value.as_ptr(), value.len())
    }

    fn config<'a>(proxy: &'a str, dns: &'a str) -> TunnelLensTun2ProxyConfig {
        let (proxy_address, proxy_address_len) = bytes(proxy);
        let (dns_address, dns_address_len) = bytes(dns);
        TunnelLensTun2ProxyConfig {
            struct_size: std::mem::size_of::<TunnelLensTun2ProxyConfig>() as u32,
            tun_fd: 7,
            proxy_type: PROXY_SOCKS5,
            proxy_address,
            proxy_address_len,
            proxy_port: 1080,
            mtu: 1500,
            username: ptr::null(),
            username_len: 0,
            password: ptr::null(),
            password_len: 0,
            dns_strategy: DNS_OVER_TCP,
            dns_address,
            dns_address_len,
            ipv6_enabled: 0,
            reserved: [0; 7],
            tcp_timeout_seconds: 600,
            udp_timeout_seconds: 10,
            max_sessions: 200,
        }
    }

    #[test]
    fn accepts_typed_socks5_and_http_profiles() {
        let socks = config("192.0.2.10", "8.8.8.8");
        let parsed = unsafe { parse_config(&socks) }.expect("valid SOCKS5 config");
        assert_eq!(parsed.args.proxy.proxy_type, ProxyType::Socks5);

        let mut http = config("2001:db8::10", "2001:4860:4860::8888");
        http.proxy_type = PROXY_HTTP;
        let parsed = unsafe { parse_config(&http) }.expect("valid HTTP config");
        assert_eq!(parsed.args.proxy.proxy_type, ProxyType::Http);
    }

    #[test]
    fn rejects_hostnames_and_invalid_ranges() {
        let hostname = config("proxy.example", "8.8.8.8");
        assert!(unsafe { parse_config(&hostname) }.is_err());

        let mut bad_mtu = config("192.0.2.10", "8.8.8.8");
        bad_mtu.mtu = 100;
        assert!(unsafe { parse_config(&bad_mtu) }.is_err());
    }

    #[test]
    fn exported_metadata_is_fixed() {
        assert_eq!(ABI_VERSION, tunnellens_tun2proxy_abi_version());
        assert_eq!(VERSION, "tun2proxy/0.8.2+tunnellens.1");
        assert!(!VERSION_C[..VERSION_C.len() - 1].contains(&0));
    }
}
