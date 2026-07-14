# Cargo.lock review

Review date: 2026-07-14

## Scope and reproducibility

The lock file at `app/src/main/rust/Cargo.lock` was generated for the
TunnelLens wrapper around vendored tun2proxy 0.8.2. The project pins Rust and
Cargo 1.88.0 in `rust-toolchain.toml`, pins the upstream source archive and
commit in `TUN2PROXY_DEPENDENCY_REVIEW.md`, and runs every Android Cargo build
with `--locked`.

Rust 1.85.0 was initially selected because that is the upstream manifest's
declared minimum. A locked check failed deterministically because
`hickory-proto 0.26.1` requires Rust 1.88 and `libloading 0.9.0` requires Rust
1.88.0. The project therefore pins 1.88.0, the lowest compiler accepted by the
resolved graph, instead of weakening or arbitrarily downgrading upstream APIs.

Lock/metadata inventory:

- 241 packages total;
- 239 packages from the crates.io registry;
- two reviewed local path packages (`tun2proxy` and the TunnelLens wrapper);
- zero Git dependencies;
- zero packages with a missing Cargo license field;
- registry package integrity is fixed by Cargo.lock checksums;
- upstream `udpgw`, `upstream-android-jni`, and `android_logger` features are
  absent from the Android feature graph.

RustSec `cargo-audit 0.22.2` scanned all 241 locked packages and reported zero
known vulnerabilities. It did report two allowed maintenance warnings:

- `RUSTSEC-2025-0069`: `daemonize 0.5.0` is unmaintained;
- `RUSTSEC-2024-0436`: `paste 1.0.15` is unmaintained.

These warnings are additional reasons not to promote the candidate blindly.
They must be removed, replaced, or explicitly risk-accepted before production
admission.

Duplicate package names are limited to nine compatibility groups:
`bitflags`, `cpufeatures`, `getrandom`, `nix`, `rand`, `rand_core`, `thiserror`,
`thiserror-impl`, and `windows-sys`. Most are platform/transitive compatibility
versions; they are fixed by the lock and are not a reason to perform blind
upgrades.

## License result — GPL route accepted, release duties active

All packages declare a license, but declaration is not the same as approval.
The graph includes common MIT, Apache-2.0, BSD, ISC, Unicode-3.0, Zlib and
Unlicense alternatives. Two entries require special attention:

- `socks5-impl 0.8.7` is `GPL-3.0-or-later` and is linked into all three
  Android binaries. The upstream minimum compatible version 0.8.3 was also
  downloaded independently and inspected; it declares the same license.
- `tun 0.8.13` declares `WTFPL`, an unusual permissive license that still needs
  an explicit release-policy decision.

On 2026-07-14 the product owner accepted GPL-3.0-or-later for the entire
TunnelLens application and its corresponding distribution obligations. The
repository now includes the complete GPL text, third-party notice inventory,
in-app legal notice, and `docs/DISTRIBUTION.md`. The `socks5-impl` license is
therefore no longer a product-decision blocker. `tun`'s unusual permissive
WTFPL notice remains part of the release-specific notice review.

This decision does not make the Rust core production-ready. Normal Gradle
builds still exclude it, and `-Ptun2proxyPoc=true` remains required for
candidate APKs until routing, lifecycle, maintenance-risk, soak, and proxy
interoperability gates pass. No binary may be distributed until its exact
Corresponding Source and release-specific dependency notices are offered as
documented in `DISTRIBUTION.md`.

This inventory is an engineering gate, not legal advice.

## Source-build result

The explicit `:app:buildRustAndroid` task successfully produced:

| Android ABI | Rust target | ELF machine | LOAD alignment |
|---|---|---|---|
| `arm64-v8a` | `aarch64-linux-android` | AArch64 | `0x4000` |
| `armeabi-v7a` | `armv7-linux-androideabi` | ARM | `0x4000` |
| `x86_64` | `x86_64-linux-android` | AMD x86-64 | `0x4000` |

Each binary depends only on Android platform `libdl.so`, `libm.so`, and
`libc.so`. No downloaded prebuilt tun2proxy library is used. Generated native
outputs stay under `app/build` and are not source-controlled.

Useful verification commands:

```text
cargo test --locked --manifest-path app/src/main/rust/Cargo.toml -p tunnellens-tun2proxy --lib
cargo audit --file app/src/main/rust/Cargo.lock
gradlew :app:buildRustAndroid
gradlew :app:assembleDebug -Ptun2proxyPoc=true
```
