# tun2proxy Native Dependency Review

## Decision

**tun2proxy 0.8.2 is license-admissible under TunnelLens's repository-wide GPL-3.0-or-later license, but remains an explicitly enabled source-level candidate and is not approved as the production runtime.** The product owner accepted the complete GPL distribution obligations on 2026-07-14. Normal builds still exclude the Rust library; `-Ptun2proxyPoc=true` is required to place it in a candidate APK. The current hev-socks5-tunnel runtime remains the default until every technical blocker below is resolved.

## Pinned source

| Item | Fixed value |
|---|---|
| Upstream | `https://github.com/tun2proxy/tun2proxy` |
| Release/tag | `v0.8.2` |
| Commit | `eed123fbbec06295bf83f9be36d5a0f64ed9a8cb` |
| Published | 2026-06-08 |
| Source archive | `https://github.com/tun2proxy/tun2proxy/archive/refs/tags/v0.8.2.tar.gz` |
| Source SHA-256 | `4d13b3aa9da2ea1d640e84e8d9b90628d971a408c1099b6383e1d4ea5191ad93` |
| Top-level license | MIT; transitive `socks5-impl` is GPL-3.0-or-later |
| Fixed Rust | 1.88.0; Rust edition 2024 |
| Upstream Android build | NDK 26.3.11579264, API 21 |
| TunnelLens target | NDK 28.2.13676358, minSdk/API 26 |
| Required ABIs | `arm64-v8a`, `armeabi-v7a`, `x86_64` |

The release also publishes `tun2proxy-android-libs.zip` with GitHub digest `sha256:903f8b780743df5a22ba44e50e1b02cbb0a190ac252ffb03c84d75ef9c0754a9`. TunnelLens does not consume that binary: production artifacts must be built from the pinned, reviewed source through the project build.

The MIT notice must be retained if source or binaries are distributed. The
combined application is GPL-3.0-or-later, and every release must also satisfy
the Corresponding Source and notice procedure in `docs/DISTRIBUTION.md`.

## Relevant upstream capabilities

- Android and caller-owned TUN fd support, including an explicit close-fd-on-drop option.
- Standard HTTP proxy transport, including unauthenticated, Basic and Digest authentication.
- SOCKS5 transport with unauthenticated and username/password authentication.
- HTTP `CONNECT` for TCP destinations, which is the required transport for HTTPS targets; TunnelLens still performs no TLS interception.
- Cancellation-token-based asynchronous shutdown and a process-global single-session guard.
- Traffic byte callbacks.
- 16 KiB linker page-size flags in the upstream Android script for 64-bit Android targets.

UDP, virtual DNS, IPv6, SOCKS4 and UdpGW are outside the first TunnelLens migration and must remain disabled or unreachable until separately designed and tested.

## Production blockers

### Resolved — GPL distribution route accepted (2026-07-14)

The reviewed `Cargo.lock` resolves `socks5-impl 0.8.7`, which declares
`GPL-3.0-or-later` and is linked into every Android target. Pinning the earliest
version accepted by upstream does not avoid the issue: the independently
downloaded `socks5-impl 0.8.3` crate declares the same license. The `tun` crate
also declares the unusual but permissive `WTFPL` license.

The product owner explicitly selected the GPL route for the whole application
and accepted its distribution obligations. TunnelLens now has a root GPL text,
an in-app legal notice, third-party notice record, and distribution checklist.
This resolves the product/license selection gate, not the technical admission
gates. Every conveyed binary must still be accompanied by or offered with its
exact Corresponding Source and release-specific dependency notices.

### P0 — embedded API may terminate the Android process

`general_run_for_api()` starts a thread after the tunnel returns; after two seconds that thread calls `std::process::exit(-1)`. This behavior is suitable only for a standalone process and is forbidden inside the TunnelLens app process.

The generic C entry point also parses arguments with Clap's terminating `parse_from()` path. Malformed or untrusted input must return an error and must never terminate the process.

Resolution implemented for the POC: the vendored source removes both forced
exit threads, the generic parser uses `try_parse_from`, and the upstream Android
JNI is disabled by default. `tunnellens-tun2proxy` provides a typed, panic-
catching C ABI that calls `general_run_async` with a cancellation token. The
production gate still requires soak testing and preferably an upstream fix.

### P0 — external proxy sockets can loop into the VPN

The upstream Android JNI is primarily designed for a local proxy and exposes no `VpnService.protect(fd)` callback for each outbound proxy socket. TunnelLens connects directly to LAN or remote Charles/Reqable endpoints, so an unprotected IPv4 proxy connection would be captured by the same VPN route.

Resolution for the first POC: resolve the proxy endpoint before creating the TUN, pass the selected numeric address to the engine, and always exclude that exact proxy address from the VPN route set. The exclusion is mandatory and independent of the user's general “bypass LAN” preference. Resolution changes and multi-address hostnames require a new connection generation. A future upstream socket-protect hook may replace this only after testing.

### P0 — dependency resolution is not reproducible

The v0.8.2 source tag ignores and does not publish `Cargo.lock`; `Cargo.toml` contains compatible version ranges. Building the tag at different times can therefore select different transitive crates.

Resolution implemented mechanically: `Cargo.lock` is generated with Cargo
1.88.0, every Gradle Cargo invocation uses `--locked`, there are no Git
dependencies, and the three ABI outputs are built from source. The license
review and accepted distribution route are recorded in `CARGO_LOCK_REVIEW.md`.

### P1 — credentials and configuration boundary

The upstream examples pass the proxy URL, including credentials, through a command-line string. TunnelLens must not expose credentials through process arguments, logs, parse errors or full native configuration dumps.

Resolution: the local FFI accepts typed byte buffers and lengths, percent-encodes only inside native memory when necessary, disables upstream informational logging, and clears temporary credential buffers after the session. Kotlin continues to decrypt credentials only for the connection scope.

### P1 — lifecycle and feature containment

Cancellation must be proven to complete without killing the process, leaking Tokio workers, retaining the TUN fd or leaving sessions alive. The first integration must explicitly keep IPv6, generic UDP, UdpGW and unreviewed DNS strategies disabled.

### P1 — unmaintained locked packages

RustSec reports no known vulnerability in the reviewed lock file, but flags
`daemonize 0.5.0` (`RUSTSEC-2025-0069`) and `paste 1.0.15`
(`RUSTSEC-2024-0436`) as unmaintained. A production candidate must eliminate
or explicitly risk-accept both after confirming their Android reachability.

## Planned integration boundary

`TunnelEngine` is now the only native-session dependency of `SocksVpnService`. `HevSocks5TunnelEngine` adapts the current JNI runtime and remains selected by `DefaultAppContainer`.

The source build, typed Rust C ABI, C/JNI loader and native smoke test now
compile, but there is deliberately no `Tun2ProxyTunnelEngine` and no Service
selection while the routing and lifecycle gates remain open. A future engine must
implement the same ownership contract:

- every start attempt consumes or closes its duplicate TUN fd;
- stop is idempotent;
- bounded await distinguishes exit from timeout;
- force-stop closes owned descriptors and sessions;
- metrics contain aggregate byte counts only;
- no engine can run concurrently with another process-local engine.

The Service state machine, foreground lifecycle, reconnect generation and cleanup owner remain unchanged by the engine selection.

## Admission tests

Before changing the default runtime:

1. Source-build all required ABIs with the pinned Rust/NDK toolchain and verify 16 KiB ELF alignment.
2. Run invalid-input, duplicate-start, stop-before-ready, stop-idle and native-error tests without process exit.
3. Run 20 rapid starts/stops, repeated reconnect, concurrent TCP sessions and a long-lived connection while checking fd/thread stability.
4. Verify proxy-route exclusion with LAN and non-LAN IPv4 endpoints and after network changes.
5. Verify SOCKS5 through Charles and Reqable, then HTTP and HTTPS targets through HTTP `CONNECT` for both tools.
6. Verify unauthenticated, correct credentials, wrong credentials, wrong protocol and unreachable endpoint results.
7. Confirm that UDP, IPv6 and DNS behavior is not claimed or silently leaked.

## Implemented POC build status

- The project pins Rust/Cargo 1.88.0 in `rust-toolchain.toml`; 1.85.0 was tested
  and rejected because the resolved `hickory-proto` and `libloading` packages
  require 1.88.
- `app/src/main/rust/Cargo.lock` contains 241 packages: 239 crates.io packages,
  two local path packages, no Git source and no missing license field.
- `:app:buildRustAndroid` builds `arm64-v8a`, `armeabi-v7a` and `x86_64` with
  NDK 28.2.13676358/API 26 and 16 KiB load-segment alignment.
- Normal Android builds exclude the candidate. Use
  `-Ptun2proxyPoc=true` for candidate tests until technical admission is complete.
- `DefaultAppContainer` still selects `HevSocks5TunnelEngine`; no VPN traffic
  reaches the Rust implementation.

## References

- Upstream repository: <https://github.com/tun2proxy/tun2proxy>
- Pinned release: <https://github.com/tun2proxy/tun2proxy/releases/tag/v0.8.2>
- Pinned commit: <https://github.com/tun2proxy/tun2proxy/commit/eed123fbbec06295bf83f9be36d5a0f64ed9a8cb>
- MIT license: <https://github.com/tun2proxy/tun2proxy/blob/v0.8.2/LICENSE>
