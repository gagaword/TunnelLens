# TunnelLens Implementation Plan

This plan follows [TECH_STACK.md](TECH_STACK.md). Phases 0–5 document the working ModernSocks-era SOCKS5 baseline. The approved TunnelLens product reset continues as R0 onward without pretending that target HTTP/HTTPS proxy support already exists.

## R0 — Product reset and application shell

Status: completed (2026-07-13)

- Adopt the formal user-facing name TunnelLens while retaining internal package/class/database identifiers during staged migration.
- Reposition the product as an Android VPN-to-debug-proxy bridge, initially targeting Charles and Reqable through standard protocols.
- Define SOCKS5 plus HTTP/HTTPS debug-proxy scope; HTTPS destinations use standard HTTP `CONNECT`, while TLS interception remains the desktop tool's responsibility.
- Replace the compact bottom navigation with a Material 3 top app bar, left navigation drawer, and right overflow menu; retain an adaptive large-screen navigation treatment.
- Replace the template launcher artwork with a TunnelLens adaptive/monochrome vector icon.
- Rewrite the technical stack, architecture boundary, development rules, README, and migration plan.
- Keep the current reviewed hev SOCKS5 runtime unchanged and buildable.

Exit criteria: branding and navigation work in both locales, current SOCKS5 behavior remains intact, documentation separates current and target capabilities, tests/Lint/build pass.

## R1 — Protocol-aware profiles

Status: planned

- Add `ProxyProtocol` with `SOCKS5` and `HTTP`; display HTTP as “HTTP/HTTPS debug proxy”.
- Migrate Room schema 2→3 with existing profiles defaulting to SOCKS5 and upgrade import/export documents compatibly.
- Add protocol selection, protocol-specific validation, SOCKS5 handshake probing, HTTP `CONNECT` probing, authentication errors, and UI tests.
- Continue using the current engine only for SOCKS5; fail closed rather than presenting HTTP profiles as connectable before R2.

Exit criteria: data migration is lossless, protocol-specific probes are deterministic, no UI claim exceeds the active runtime.

## R2 — Reviewed multi-protocol tunnel engine

Status: in progress — GPL route accepted; technical admission remains (2026-07-14)

- Review tun2proxy as the primary candidate: pin source/release/checksum, review the complete transitive license graph, Rust/NDK build reproducibility, TUN fd ownership, Android socket protection, ABI and 16 KiB page compatibility.
- Introduce a narrow `TunnelEngine` boundary and migrate statistics, native-exit reporting, bounded stop and cleanup semantics.
- Support IPv4 TCP through both SOCKS5 and standard HTTP proxy paths; do not enable UDP, DNS, or IPv6 merely because the candidate exposes them.
- Preserve hev as a rollback baseline until the new engine passes equivalent tests and soak.

Current gate: the pinned Rust 1.88/Cargo.lock build, non-terminating typed FFI and three ABI POC build are implemented. The product owner accepted GPL-3.0-or-later for the full application and its distribution duties on 2026-07-14, so the license-choice blocker is resolved. Production integration remains blocked by mandatory proxy-route exclusion, lifecycle/soak evidence, maintenance-risk disposition, and the Charles/Reqable SOCKS5 plus HTTP/HTTPS interoperability matrix in `TUN2PROXY_DEPENDENCY_REVIEW.md`. Normal APKs exclude the Rust candidate.

Exit criteria: no routing loop, no credential/config logging, all exit paths are bounded and leak-free, and the three configured ABIs build from reviewed source.

## R3 — Charles and Reqable interoperability

Status: planned

- Verify SOCKS5, plain HTTP, HTTPS destination via CONNECT, WebSocket-over-HTTP, proxy exit/restart and protocol mismatch against both tools.
- Add setup and diagnostic guidance for listener binding, access control, CA trust, certificate pinning, QUIC/HTTP3, and LAN firewall behavior without tool-specific forwarding code.
- Run long-session, concurrent-request, 20-cycle rapid start/stop, multi-round reconnect, network switch, lock/sleep and resource cleanup tests.

Exit criteria: the published compatibility matrix is reproduced on Pixel 6 and at least one additional Android API/device target.

## Phase 0 — Project baseline

Status: completed (2026-07-12)

- Replace the generated XML/ViewBinding/NDK sample with one Compose-only `ComponentActivity`.
- Keep AGP 9.1.1, Gradle 9.3.1, built-in Kotlin 2.2.21, JDK 21, compile SDK 36.1, target SDK 36, and minimum SDK 26 after compatibility checks.
- Configure stable Material 3/Compose, Lifecycle, Navigation Compose, Room, Preferences DataStore, kotlinx.serialization, coroutines, and test dependencies through the Version Catalog.
- Add a light/dark/dynamic-color theme, responsive three-destination navigation, and Home, Profiles, and Settings screens.
- Add an explicit `ConnectionState`, `VpnController`, and in-process `FakeVpnController`. No `VpnService`, permission, TUN, JNI, or native dependency is included.
- Add immutable `HomeUiState`, `HomeViewModel`, previews, ViewModel tests, and a Home Compose UI test.
- Verify unit tests, Android tests when a device is available, Android Lint, and `assembleDebug`.

Exit criteria: the application builds, unit tests pass, no new high-severity Lint issue remains, and the Home screen is usable without any real network or VPN operation.

## Phase 1 — UI and configuration management

Status: completed (2026-07-12)

- Define validated profile and settings models without storing plaintext credentials.
- Implement Room entities/DAO/database/repository with Flow and migrations from schema version 1 onward.
- Implement Preferences DataStore for theme and ordinary behavior settings.
- Implement an Android Keystore-backed AES-GCM credential store with versioned ciphertext envelopes and negative-path tests.
- Build Profiles list, profile editor, selection, delete/undo, import/export without credentials by default, and Settings theme controls.
- Provide complete English and Simplified Chinese resources, automatic Android LocaleConfig, and an in-app language selector backed by the platform/AppCompat per-app locale APIs.
- Continue using `FakeVpnController`; add repository, database, serialization, security, ViewModel, and Compose tests.

Exit criteria: profiles and settings survive process restart, validation and corrupt-input handling are tested, credentials never persist or log in plaintext, and accessibility/UI tests cover the critical flows.

## Phase 2 — VpnService framework

Status: completed and verified on a Pixel 6 running Android 13 (2026-07-12)

- Add the VPN permission Activity Result flow, foreground `VpnService`, notification channel/actions, and required manifest declarations only at this point.
- Implement the process-level connection state store and an explicit state machine with generation tokens.
- Establish and close a test TUN without forwarding traffic; centralize idempotent cleanup for stop, revoke, destroy, cancellation, and failure.
- Add lifecycle/state-machine tests and document the state transitions and resource ownership in `docs/VPN_LIFECYCLE.md`.

Exit criteria: permission, start/stop/revoke, repeated actions, process/service recreation, and descriptor cleanup are verified on supported emulator/device APIs. No native forwarding is present.

## Phase 3 — IPv4 TCP MVP

Status: completed, including bounded stop and reconnect soak verification (2026-07-12)

- Review the upstream license and pin an exact `hev-socks5-tunnel` release/commit in `docs/NATIVE_DEPENDENCY_REVIEW.md` before adding source or binaries.
- Integrate the pinned source through NDK/CMake for `arm64-v8a`, `armeabi-v7a`, and `x86_64`; add a minimal validated JNI bridge.
- Document and test TUN file-descriptor ownership, upstream socket protection, native exit reporting, log redaction, and bounded stop behavior.
- Support IPv4 TCP SOCKS5 with no authentication or protected username/password authentication. Do not claim UDP, IPv6, or advanced DNS support.

Exit criteria: real traffic works against test infrastructure, upstream traffic cannot loop into the TUN, stop/error paths release all resources, and the native license/build provenance is complete.

## Phase 4 — Reliability and observability

Status: completed and verified on a Pixel 6 running Android 13 (2026-07-13)

- Add structured error categories, timeouts, network callbacks, bounded exponential reconnect, and stale-generation protection.
- Add duration and byte statistics without collecting destinations or sensitive content.
- Test network switching, lock/sleep, long-running sessions, process pressure, competing VPNs, authentication failure, and leak-prone exit paths.

Exit criteria: deterministic errors do not retry indefinitely, user stop never reconnects, and soak/resource tests meet documented thresholds.

Implemented validation includes real byte/duration statistics, proxy health failure and recovery, bounded retry exhaustion, user stop during retry, total underlying-network loss/recovery, foreground survival while locked, unit tests, Lint, all three configured native ABIs, and 17/17 connected device tests after unlocking the test device.

## Phase 5 — Advanced networking, one capability at a time

Status: in progress; capabilities 5.1 per-app routing and 5.2 LAN bypass completed and verified (2026-07-13)

- Evaluate and deliver UDP, remote DNS, IPv6, per-app routing, and LAN bypass as separate changes with explicit leak behavior and user-facing limitations.
- Never add `::/0`, UDP claims, or allowed/disallowed application rules before the corresponding implementation and leak tests pass.

Exit criteria: each capability has protocol, failure, privacy, device, and regression coverage before the next begins.

Capability 5.1 adds mutually exclusive all-app, only-selected, and bypass-selected policies per profile. Room migration 1→2, package visibility, app search/filter/selection UI, fail-closed empty allow-list handling, Builder integration, and device leak-direction tests are complete.

Capability 5.2 activates the existing per-profile LAN-bypass preference. A deterministic IPv4 route complement keeps private, loopback, link-local, multicast, and limited-broadcast destinations outside the TUN on every supported API level while all other IPv4 destinations remain routed. Unit tests cover CIDR boundaries and a Pixel 6 device test verifies both direct-LAN and tunneled behavior. UDP, remote DNS, and IPv6 remain disabled and unclaimed.

## Legacy Phase 6 — Release readiness (superseded by TunnelLens R5)

- Complete security/privacy documentation, third-party notices, backup rules, R8 configuration, native symbols, crash symbolization, and release CI.
- Verify the current Play target API, VPN/foreground-service declarations, supported devices/ABIs, accessibility, battery behavior, and privacy disclosures.
- Configure signing outside the repository; never commit keys or developer account credentials.

Exit criteria: release checklist, license review, compatibility matrix, privacy material, signed external build process, and full regression suite are complete.
