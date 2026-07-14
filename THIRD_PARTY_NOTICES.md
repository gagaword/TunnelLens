# TunnelLens third-party notices

TunnelLens as a combined application is distributed under GPL-3.0-or-later.
That project license does not remove the copyright, attribution, source-offer,
or notice requirements of included third-party works. The original license
files listed below are authoritative and must remain with Corresponding Source.

## Native code shipped by normal Android builds

| Component | Fixed source | License | Preserved notice |
|---|---|---|---|
| hev-socks5-tunnel | 2.14.4, commit `4d6c334dbfb68a79d1970c2744e62d09f71df12f` | MIT | `app/src/main/cpp/third_party/hev-socks5-tunnel/LICENSE` |
| hev-socks5-core | vendored by hev-socks5-tunnel 2.14.4 | MIT | `app/src/main/cpp/third_party/hev-socks5-tunnel/src/core/LICENSE` |
| hev-task-system | vendored by hev-socks5-tunnel 2.14.4 | MIT | `app/src/main/cpp/third_party/hev-socks5-tunnel/third-part/hev-task-system/LICENSE` |
| lwIP | vendored by hev-socks5-tunnel 2.14.4 | BSD-style | `app/src/main/cpp/third_party/hev-socks5-tunnel/third-part/lwip/LICENSE` |
| libyaml-derived YAML implementation | vendored by hev-socks5-tunnel 2.14.4 | MIT | `app/src/main/cpp/third_party/hev-socks5-tunnel/third-part/yaml/License` |

The exact source, checksums, local changes, ABI build, and omitted Windows-only
Wintun component are recorded in `docs/NATIVE_DEPENDENCY_REVIEW.md`.

## Rust multi-protocol candidate

`tun2proxy` 0.8.2 is vendored under
`app/src/main/rust/third_party/tun2proxy` and retains its MIT `LICENSE`. Its
locked graph includes `socks5-impl 0.8.7` under GPL-3.0-or-later and
`tun 0.8.13` under WTFPL. The whole graph, checksums, and risk findings are
recorded in `app/src/main/rust/Cargo.lock`, `docs/CARGO_LOCK_REVIEW.md`, and
`docs/TUN2PROXY_DEPENDENCY_REVIEW.md`.

Normal APKs still exclude the Rust candidate while technical admission gates
remain open. A build produced with `-Ptun2proxyPoc=true` is a GPL-covered
combined work and may not be distributed without its exact locked
Corresponding Source and every applicable dependency notice.

## Kotlin, AndroidX, Compose, and build dependencies

The application also uses the Kotlin toolchain, AndroidX, Jetpack Compose,
Material Components, kotlinx.coroutines, kotlinx.serialization, Room,
DataStore, and their resolved dependencies. Version coordinates are centralized
in `gradle/libs.versions.toml`; the exact graph is resolved by Gradle.

Before any public or third-party binary distribution, the release owner must
generate and review the exact dependency-license inventory for that build,
bundle all required notices with the release, and archive the inventory beside
the Corresponding Source. This repository does not treat this summary as a
substitute for the original license texts or a release-specific inventory.

This file is an engineering record, not legal advice.
