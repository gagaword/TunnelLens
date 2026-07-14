# Phase 5.2 — Local-network Bypass Validation

Date: 2026-07-13  
Device: Pixel 6, Android 13, arm64-v8a

## Behavior and leak boundary

- `bypassLan = false` installs the IPv4 default route and sends every IPv4 destination into the VPN.
- `bypassLan = true` leaves RFC 1918 private space (`10/8`, `172.16/12`, `192.168/16`), loopback (`127/8`), IPv4 link-local (`169.254/16`), multicast (`224/4`), and limited broadcast (`255.255.255.255/32`) outside the VPN.
- All IPv4 addresses adjacent to those exclusions remain routed into the TUN. The implementation calculates the route complement rather than broad public-address approximations.
- The same route set is used from API 26 upward; API 33 `excludeRoute()` is not required.
- The policy does not call `allowBypass()` and therefore does not let arbitrary applications opt out. It composes with the mutually exclusive per-app routing policy.
- Protected SOCKS upstream sockets continue to bypass the TUN independently. UDP and VPN DNS remain disabled. No IPv6 address, route, or DNS server is added, so Android's default Builder behavior blocks that unconfigured family instead of leaking it outside the VPN.

## Automated verification

- Unit tests cover disabled/default routing, every excluded class, adjacent public boundaries, uniqueness, and canonical route generation.
- Room instrumentation verifies that a disabled bypass value reaches the selected `ConnectionProfile`.
- Final gate: 37/37 JVM unit tests and 21/21 connected tests passed; Lint reported 0 errors; `assembleDebug` packaged arm64-v8a, armeabi-v7a, and x86_64.

## Device routing verification

A local-only SOCKS5 fixture was exposed to the device through `adb reverse`; no real proxy endpoint or credential was used.

1. With LAN bypass enabled, Chrome opened `http://192.168.1.1/` directly and rendered the physical gateway's login page.
2. With LAN bypass disabled, a fresh path under the same private destination returned the fixture response `ModernSocks Phase 4 OK`, proving that the request entered the TUN and SOCKS path.
3. The VPN, reverse mapping, and exact fixture processes were stopped after the test.

## Remaining phase boundary

UDP cannot be enabled by removing the Phase 3 ingress filter alone: that filter fixed a reproduced lwIP scheduler shutdown hang. Remote DNS depends on an explicit, separately tested transport strategy. IPv6 requires dual-stack native, DNS, route, and leak coverage. Each remains gated as a separate Phase 5 capability.
