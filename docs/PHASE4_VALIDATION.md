# Phase 4 Validation

Date: 2026-07-13  
Device: Pixel 6, Android 13, arm64-v8a

## Implemented behavior

- Structured transient and deterministic connection failures.
- Ten-second SOCKS5 connect/authentication probe with credential buffers cleared after use. The startup probe runs before TUN establishment without `VpnService.protect()`; connected health probes remain protected.
- Internet/non-VPN `NetworkCallback` tracking.
- Generation-safe reconnect delays of 1, 2, 5, 10, and 30 seconds, limited to five attempts.
- Periodic proxy health probing; two consecutive reachability failures start recovery.
- Aggregate monotonic connected duration plus hev upload/download byte statistics.
- Reconnect can always be stopped by the user and shares the bounded cleanup path.

## Automated verification

- `gradlew testDebugUnitTest`: 37 tests, 0 failures, 0 errors (current full suite, including later-phase regressions).
- `gradlew lintDebug`: success; 0 errors and 11 dependency-update warnings only.
- `gradlew assembleDebug`: success for arm64-v8a, armeabi-v7a, and x86_64.
- `gradlew connectedDebugAndroidTest`: 21/21 tests passed on the unlocked Pixel 6 (current full suite, including later-phase regressions).

## Manual device verification

- Connected to a local-only SOCKS5 fixture through ADB reverse and observed increasing duration, upload, and download totals.
- Removed the proxy endpoint: two failed health checks entered bounded reconnect; restoring the endpoint during attempt four returned to Connected with counters preserved.
- Stopped during reconnect, restored the proxy, and confirmed the app stayed Disconnected.
- Disabled both Wi-Fi and mobile data: the final underlying-network loss entered Reconnecting; restoring networks returned to Connected.
- Locked the device for ten seconds and confirmed the VPN foreground Service and ongoing stop notification remained active.
- The earlier Phase 3 soak remains applicable to unchanged native shutdown: 20 rapid cycles, five concurrent browser rounds, and a 60-second session completed without a stop timeout.
- A real LAN Charles 5.1 SOCKS endpoint at port 8889 completed the startup greeting from a Pixel 6 after removing the premature pre-TUN `protect()` call; the app reached IPv4 TCP Connected and reported bidirectional traffic. Connected health/native sockets retain protection.

## Remaining release risks

Competing-VPN behavior and longer battery/process-pressure soak remain release-risk tests and should be repeated with dedicated device infrastructure.
