# TunnelLens

TunnelLens is an Android VPN-to-debug-proxy bridge. It routes system-wide or selected-app traffic to a desktop debugging proxy without relying on Android Wi-Fi proxy behavior. Charles and Reqable are the first interoperability targets, but the product is based on standard SOCKS5 and HTTP/HTTPS proxy semantics rather than vendor-specific integration.

The current working runtime remains the reviewed, source-built `hev-socks5-tunnel` 2.14.4 baseline and therefore forwards IPv4 TCP to SOCKS5 today. The approved next stages add protocol-aware profiles and a reviewed multi-protocol engine for standard HTTP proxying and HTTPS destinations through `CONNECT`. UDP, VPN-managed DNS, IPv6, on-device traffic inspection, CA management, and certificate-pinning bypass are not currently supported.

Android `VpnService` owns the TUN. The app provides bounded reconnect, aggregate statistics, per-app routing, optional local-network bypass, encrypted credentials, and explicit resource cleanup. HTTPS inspection remains the responsibility of the configured desktop tool and the target application's certificate trust policy.

The Compose/Material 3 interface is available in English and Simplified Chinese, with light, dark, and dynamic-color themes. `ModernSocks` remains in some internal package, class, database, and build identifiers during the staged migration so existing app data and native integration are not destructively renamed.

Source code and immutable release history are published at
<https://github.com/gagaword/TunnelLens>. Release binaries link to the exact
tag and commit from which they were built.

## Build

Use Android Studio with the project-compatible JDK and installed Android SDK 36.1, or run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Run device UI tests when an emulator or device is connected:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Maintainers create signed release APKs with `tools/build-release.ps1`. Signing
keys and credentials remain outside the repository.

See [docs/TECH_STACK.md](docs/TECH_STACK.md), [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/VPN_LIFECYCLE.md](docs/VPN_LIFECYCLE.md), and [docs/SECURITY.md](docs/SECURITY.md) for constraints and status.

## License

Unless a file or vendored component says otherwise, the original TunnelLens
application code is free software licensed under the **GNU General Public
License, version 3 or (at your option) any later version**. See [LICENSE](LICENSE)
for the complete terms. TunnelLens is provided without warranty to the extent
permitted by applicable law.

Third-party components keep their own copyright and license notices; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Anyone distributing an APK or
other object-code build must also provide the exact machine-readable
Corresponding Source and the applicable build/install information. The release
procedure and unresolved public-distribution prerequisites are documented in
[docs/DISTRIBUTION.md](docs/DISTRIBUTION.md).
