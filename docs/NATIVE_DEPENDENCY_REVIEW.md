# Native Dependency Review

> This document records the currently shipped hev-socks5-tunnel runtime. The proposed multi-protocol replacement is reviewed separately in `docs/TUN2PROXY_DEPENDENCY_REVIEW.md`; it is not yet a production dependency.

## Decision

ModernSocks Phase 3 vendors the official source release of **hev-socks5-tunnel 2.14.4** and builds it locally with the Android NDK. No prebuilt executable or shared library is accepted.

| Item | Fixed value |
|---|---|
| Upstream | `https://github.com/heiher/hev-socks5-tunnel` |
| Release/tag | `2.14.4` |
| Commit | `4d6c334dbfb68a79d1970c2744e62d09f71df12f` |
| Release date | 2026-02-06 |
| Source asset | `hev-socks5-tunnel-2.14.4.tar.xz` |
| Source SHA-256 | `90e06a5dc0c139c335d50f5a1645672113a366d30066bc5ff056ab73dc39a24d` |
| Upstream license | MIT |
| Android build | NDK `ndk-build`, invoked by AGP external native build |
| Pinned local NDK | `28.2.13676358` |
| Application API floor | Android API 26 |
| Packaged ABIs | `arm64-v8a`, `armeabi-v7a`, `x86_64` |

The archive's `.rev-id` is `4d6c334`, matching the fixed release commit and the signed GitHub release tag. The release remains actively maintained at review time. Upgrades require a new checksum, commit/license review, ABI builds, socket-protection regression test, and traffic/cleanup regression run; the vendored directory must never track an unfixed branch.

## License compatibility

- hev-socks5-tunnel, hev-socks5-core, hev-task-system, and the bundled YAML implementation use the MIT license.
- bundled lwIP uses its permissive BSD-style license.
- copyright and license files remain inside the vendored source and must be carried into future third-party notices.
- the release archive also contains a Windows-only Wintun prebuilt dependency with a separate restrictive binary license. ModernSocks does not build or use Windows code, so `third-part/wintun` is deliberately excluded from the Android vendored tree and APK.

These permissive components may be distributed inside the GPL-3.0-or-later
TunnelLens application while their original notices are preserved. No source
of unknown provenance is built or packaged by this integration.

## API and build assessment

The upstream supported library API is used as published:

- `hev_socks5_tunnel_main_from_str(config, length, tun_fd)` starts and blocks until shutdown or failure;
- `hev_socks5_tunnel_quit()` requests shutdown;
- `hev_socks5_tunnel_stats()` exposes counters.

The official Android build is based on `Android.mk`/`ndk-build`. ModernSocks supplies its own top-level Android makefile and a thin JNI bridge. The bridge is linked into the same final shared library and uses the linker's `--wrap=socket` facility so every IPv4/IPv6 socket created by the native tunnel is passed to the active `VpnService.protect(fd)` callback before use. Because hev runs cooperative tasks on custom stacks, each Java protection call is invoked by a short-lived JVM-attached helper pthread; completion is returned through a nonblocking pipe consumed with hev cooperative I/O so the scheduler pthread is never blocked. A protection failure closes the new socket and fails closed instead of allowing a routing loop.

`--wrap=close` tracks protected sockets for bounded shutdown, and `--wrap=hev_socks5_session_udp_new` rejects UDP sessions so the Phase 3 binary cannot silently claim UDP forwarding. The upstream Android JNI source is excluded in favor of the validated ModernSocks bridge.

The final shared library uses 16 KiB-compatible linker page-size flags inherited from upstream. Release builds retain native debug symbols in the standard Android Gradle native-symbol output; packaging and upload policy is finalized in Phase 6.

## Local integration changes

Upstream files are kept under `app/src/main/cpp/third_party/hev-socks5-tunnel`, with the unused Wintun directory excluded. Two reviewed changes are applied to `src/hev-socks5-tunnel.c`: the official `cf312ec` one-line initialization fix and the compile-time guarded Phase 3 IPv4/TCP ingress filter. The native version reports the local patch level as `2.14.4-4d6c334-ms1`. ModernSocks also adds:

- a top-level `Android.mk` selecting only the three supported ABIs and composing the upstream static dependencies;
- `modern_socks_jni.c` for input validation, lifecycle serialization, short-lived Java socket-protection helpers, bounded socket/TUN shutdown, stop, running status, and version;
- Kotlin configuration and runtime adapters.

The TUN descriptor remains owned by `SocksVpnService`. Native receives a duplicated descriptor and owns/closes only that duplicate after the blocking main call returns. This makes Service cleanup and native shutdown independently idempotent without double-closing one fd number.

## Known risks

- The upstream runtime is process-global, so ModernSocks permits one native session at a time and serializes start/stop.
- `hev_socks5_tunnel_quit()` is cooperative. Service shutdown uses a bounded wait, then shuts down tracked protected sockets and closes native's TUN duplicate. Failure after the fallback remains a structured stop timeout; the completed Phase 3 soak does not replace longer Phase 4 device coverage.
- The former Android stop timeout was localized to unsupported UDP entering lwIP before the existing UDP-session rejection. A compile-time `MODERNSOCKS_IPV4_TCP_ONLY` guard now drops non-IPv4/TCP packets immediately after TUN read, before lwIP protocol dispatch. This is a Phase 3 product-boundary patch, not a claim that upstream UDP is defective.
- The one-line upstream main-branch fix `cf312ec` (`res` initialization in `hev_socks5_tunnel_stop`) is backported. It fixes an official compiler/undefined-value issue but was not the runtime stop root cause.
- The native core remains IPv4 TCP only. UDP, remote DNS, and IPv6 routing are not claimed. Reconnect, per-app routing, and LAN bypass are implemented above the native boundary and do not alter the vendored core.
- Native configuration exists only in bounded memory for the blocking start call. It is never written to disk or logged, but immutable JVM strings cannot be reliably zeroized; Kotlin limits their lifetime and native copies are cleared before release.

## Review sources

- <https://github.com/heiher/hev-socks5-tunnel/releases/tag/2.14.4>
- <https://github.com/heiher/hev-socks5-tunnel/blob/2.14.4/LICENSE>
- <https://github.com/heiher/hev-socks5-tunnel/tree/2.14.4#api>
