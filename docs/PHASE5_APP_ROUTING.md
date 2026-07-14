# Phase 5.1 — Per-app Routing Validation

Date: 2026-07-13  
Device: Pixel 6, Android 13, arm64-v8a

## Behavior and privacy boundary

- Per-profile modes: all applications, only selected applications, or bypass selected applications.
- Allow and bypass policies are mutually exclusive by type and by `VpnService.Builder` usage.
- Empty `ONLY_SELECTED` cannot be saved. Corrupt/stale storage with no installed selected app fails connection instead of becoming a global VPN.
- App discovery is limited to launchable applications declared visible by the launcher intent query. No `QUERY_ALL_PACKAGES` permission is requested.
- Package selections are stored in Room but omitted from configuration export because they are device-specific application-inventory data.

## Automated verification

- Unit tests cover codec validation, unsafe empty allow lists, and ViewModel persistence.
- Android tests cover Room persistence, migration 1→2, and Compose selection behavior.
- `gradlew testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest`: successful.
- Connected suite: 20/20 passed on Pixel 6.
- Native output remains available for arm64-v8a, armeabi-v7a, and x86_64.

## Device routing verification

With the local SOCKS5 fixture and an IPv4 test destination:

1. `ONLY_SELECTED` contained ABBANK and excluded Chrome. A Chrome request left TUN counters at 0 B upload and 0 B download while duration increased.
2. `BYPASS_SELECTED` contained ABBANK. The same Chrome request entered the TUN and produced approximately 116.7 KB upload and 18.1 KB download.

This confirms both routing directions without enabling UDP, VPN DNS, IPv6, or LAN bypass.

## Next capability

Keep the one-capability gate. Evaluate UDP separately before remote DNS, because remote DNS through the SOCKS tunnel depends on a deliberately tested UDP strategy. Revisit the Phase 3 lwIP/UDP shutdown regression before removing the current ingress rejection.
