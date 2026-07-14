# TunnelLens vendoring record

- Upstream: `https://github.com/tun2proxy/tun2proxy`
- Release: `v0.8.2`
- Commit: `eed123fbbec06295bf83f9be36d5a0f64ed9a8cb`
- Source archive: `https://github.com/tun2proxy/tun2proxy/archive/refs/tags/v0.8.2.tar.gz`
- Source archive SHA-256: `4d13b3aa9da2ea1d640e84e8d9b90628d971a408c1099b6383e1d4ea5191ad93`
- License: MIT (`LICENSE` is retained beside this file)
- Vendored on: 2026-07-14

The project builds this source with Rust 1.88.0 and Android NDK 28.2.13676358
for `arm64-v8a`, `armeabi-v7a`, and `x86_64`. No upstream prebuilt library is
used.

The reviewed Cargo graph is not MIT-only. `socks5-impl 0.8.7` declares
`GPL-3.0-or-later` and is linked into Android builds; `tun 0.8.13` declares
`WTFPL`. This candidate is therefore excluded from normal APKs and is not
approved for distribution. See `docs/CARGO_LOCK_REVIEW.md` before enabling the
explicit POC build.

Upstream declares Rust 1.85 as its minimum, but the resolved v0.8.2 dependency
graph contains `hickory-proto 0.26.1` and `libloading 0.9.0`, which require
Rust 1.88. The project therefore pins the lowest toolchain accepted by the
reviewed lock file instead of relying on a misleading lower compiler version.

## TunnelLens patches

1. The upstream Android JNI entry point is feature-gated and disabled by
   default. TunnelLens uses a typed C ABI and does not pass credential-bearing
   command-line strings.
2. The generic C API uses Clap's non-terminating `try_parse_from` path.
3. The forced `std::process::exit(-1)` teardown threads are removed from the
   generic C API and Windows service paths.
4. Build metadata is deterministic and records the reviewed upstream commit
   instead of the local parent Git state and wall-clock build time.
5. The upstream Android JNI-only dependencies are optional when that disabled
   feature is not requested, including its JNI-specific error variant.
6. Traffic counters can be reset per TunnelLens session and a removed callback
   is no longer retained as an empty callback object.

Keep these patches small and re-review/rebase them explicitly when updating the
vendored upstream version.
