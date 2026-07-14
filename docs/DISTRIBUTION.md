# GPL distribution procedure

TunnelLens is licensed as a whole under GPL-3.0-or-later. This document turns
that product decision into release gates. It is an engineering checklist, not
legal advice; a release owner remains responsible for the actual distribution
method and jurisdiction.

## What must accompany a binary

For every APK, app bundle, native library, or other object-code build conveyed
to another party:

1. Provide the complete machine-readable Corresponding Source for that exact
   build under GPL-3.0-or-later, using one of the methods allowed by GPLv3
   section 6. For downloads, offer equivalent source access next to the binary
   at no additional charge and keep clear directions to it.
2. Include the complete `LICENSE`, this repository's build scripts, Version
   Catalog, Gradle wrapper metadata, `rust-toolchain.toml`, `Cargo.lock`, pinned
   vendored native source, local patches, JNI/FFI source, and any interface
   definitions needed to build, install, run, and modify the delivered work.
3. Preserve copyright, license, no-warranty, attribution, and modification
   notices. Include a release-specific copy of `THIRD_PARTY_NOTICES.md` plus the
   original license texts required by the resolved dependency graph.
4. Mark TunnelLens modifications and their relevant dates in the source release
   notes. Do not present upstream authors as warranting local changes.
5. Do not add downstream EULA, DRM, patent, store, or contract terms that impose
   further restrictions on rights granted by the GPL.

## Reproducible source bundle

Archive a release manifest containing:

- application version name/code, build type, Git commit or immutable source
  archive checksum, and release date;
- Gradle, AGP, Kotlin, JDK, Android SDK/NDK, Rust, and Cargo versions;
- Gradle dependency graph and reviewed license inventory;
- `Cargo.lock` checksum, `cargo metadata --locked` output, RustSec result, and
  the three Android ABI checksums when the Rust core is included;
- native source archive/commit/checksum, applied local changes, ELF ABI and
  16 KiB alignment results;
- exact commands and non-secret environment requirements needed to rebuild;
- checksums for the distributed binary and Corresponding Source archive.

Do not include proxy credentials, signing private keys, tokens,
`local.properties`, or machine-specific absolute paths. Android app-signing
private keys are release secrets, not repository content. If TunnelLens is
conveyed as part of a locked User Product for which the distributor retains the
ability to install modified builds, obtain legal review of GPLv3 section 6's
Installation Information requirements before distribution.

## In-app legal notice

Settings contains a prominent free-software notice, the no-warranty statement,
and an offline copy of GNU GPL version 3. The canonical copy also remains at
the repository root. English is the controlling license text; the surrounding
UI explanation is localized in English and Simplified Chinese.

## Current release gate

No public source-download location or release-specific dependency-license
inventory has been supplied yet. Do not publish or hand out a production APK
until both exist beside the binary. A source URL must be a real, durable
location selected by the release owner; the project deliberately does not
invent a placeholder URL.

License acceptance removes the former tun2proxy GPL product-decision blocker.
It does **not** approve tun2proxy as the production runtime. Normal builds keep
using hev-socks5-tunnel, and the Rust candidate remains behind
`-Ptun2proxyPoc=true` until routing, lifecycle, soak, and Charles/Reqable
interoperability admission tests pass.

## Release sign-off

- [ ] Exact binary and source archive checksums recorded.
- [ ] Corresponding Source is complete and builds with documented tools.
- [ ] Binary and source are offered together in a GPLv3-compliant manner.
- [ ] Root GPL text and in-app legal notice are present.
- [ ] Third-party dependency graph and original notices are reviewed/bundled.
- [ ] Local modifications and dates are identified.
- [ ] No secrets or machine-local paths are present.
- [ ] Store/distribution terms impose no further restrictions.
- [ ] Installation Information question is resolved for the delivery model.
- [ ] Native admission and regression suites pass for the selected runtime.
