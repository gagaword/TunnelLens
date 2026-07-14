# TunnelLens Development Rules

## Scope and architecture

- Application-layer code is Kotlin; UI is Compose-only with Material 3.
- Keep a single Activity. Do not introduce Fragments, XML screen layouts, View Binding, Data Binding, or LiveData.
- Use immutable `UiState`, `StateFlow`, coroutines, `collectAsStateWithLifecycle()`, and unidirectional data flow.
- Keep Compose UI, repositories, credential storage, VPN orchestration, Android services, and the native tunnel separated. Dependencies point inward through interfaces.
- Keep the initial project as one `app` module until a concrete build or isolation need justifies another module.
- Use stable, mutually compatible Gradle, AGP, Kotlin, Compose, JDK, SDK, and AndroidX versions managed through the Version Catalog.
- The approved product scope is an Android VPN-to-debug-proxy bridge with SOCKS5 and standard HTTP/HTTPS proxying (`CONNECT` for HTTPS destinations). Do not expand to subscriptions, proxy chains, circumvention protocols, or on-device MITM without another approved phase change.

## VPN and native safety

- Use Android `VpnService`. The current SOCKS5 runtime remains the fixed, reviewed `hev-socks5-tunnel` until a multi-protocol core passes its own review and migration gate; never use BadVPN tun2socks or an unknown prebuilt binary.
- Do not add, replace, or upgrade a native tunnel before documenting its source, license, pinned release/commit, checksum, ABI support, build method, local changes, lifecycle behavior, and risks.
- The whole TunnelLens application is licensed under GPL-3.0-or-later. Keep the root license, in-app legal notice, third-party notices, and `docs/DISTRIBUTION.md` release gates intact; every conveyed binary must have an exact Corresponding Source offer and a reviewed dependency-license inventory.
- Model VPN start, stop, reconnect, revoke, and failure as an explicit state machine. Cleanup and stop operations must be idempotent.
- Close every TUN descriptor, socket, `NetworkCallback`, coroutine scope, temporary sensitive file, and native resource on every exit path.
- Never log credentials, decrypted secrets, authentication data, full native configurations, or sensitive Intent/URI contents.
- Protect credentials with Android Keystore and standard authenticated encryption such as AES-GCM. Never invent cryptography or persist plaintext credentials.
- TunnelLens routes traffic but does not capture content, issue/install MITM certificates, change another app's trust policy, or bypass certificate pinning.
- Do not add VPN, notification, foreground-service, or unrelated permissions until the implementation phase requires them.

## UI and data rules

- Composables render state and send actions; they do not call databases, Services, JNI, or mutable repositories directly.
- Route composables obtain ViewModels; screen composables remain stateless where practical for previews and tests.
- Use Room with Flow for proxy profiles, Preferences DataStore for ordinary settings, and kotlinx.serialization for import/export.
- Keep the formal user-facing product name `TunnelLens`; internal `ModernSocks` package/class/database identifiers may remain during the staged migration to avoid destructive renames.
- Put all user-visible text in string resources. Preserve dark mode, dynamic color fallback, font scaling, semantic labels, touch target sizing, and reasonable compact/large-screen behavior.
- Keep the default English resources and Simplified Chinese (`values-zh-rCN`) resources complete and in sync. Mark identifiers and preview-only constants as non-translatable, and run Lint after every string change.
- Do not add placeholder abstractions, modules, or dependencies without a current requirement.

## Testing and delivery

- Add focused unit tests for ViewModels, state transitions, validation, repositories, security, and lifecycle logic as those behaviors appear.
- Treat Charles and Reqable as the initial interoperability fixtures, not as hard-coded product dependencies; test both SOCKS5 and HTTP/HTTPS proxy paths before claiming multi-protocol support.
- Add Compose UI tests for visible states, user actions, navigation, accessibility semantics, and critical dark-theme layouts.
- For relevant changes, run the narrowest useful tests, `lintDebug`, and `assembleDebug`. Run connected Android tests when a device/emulator is available; otherwise report the exact limitation and command.
- Fix compilation failures and high-severity Lint findings introduced by the change before handoff.
- Keep documentation consistent with the implemented phase and report commands, outcomes, unrun checks, limitations, and risks.

## Repository hygiene

- Preserve unrelated user work. Never use destructive Git commands such as `git reset --hard` or discard changes without explicit approval.
- Do not commit real proxy endpoints, passwords, tokens, signing keys, developer credentials, `local.properties`, generated native outputs, or machine-specific absolute paths.
- Treat imported configuration, files, URIs, Intents, database contents, and native input as untrusted.
