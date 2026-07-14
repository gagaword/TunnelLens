# TunnelLens Architecture

## Product-reset target boundary

TunnelLens is an Android VPN-to-debug-proxy bridge. It routes selected Android IPv4 TCP flows to a user-configured desktop proxy; it does not inspect, persist, decrypt, or modify request content. Charles and Reqable are the first interoperability fixtures, while the production model uses only standard SOCKS5 and HTTP proxy semantics.

`SocksVpnService` depends on the process-local `TunnelEngine` interface rather than JNI directly. `DefaultAppContainer` currently supplies `HevSocks5TunnelEngine`, which adapts and preserves the reviewed hev lifecycle. The source-built tun2proxy POC has a typed Rust C ABI and JNI loader. TunnelLens now accepts GPL-3.0-or-later for the complete application, but normal builds still exclude the candidate and no `Tun2ProxyTunnelEngine` exists because routing, lifecycle, soak, maintenance-risk, and proxy-interoperability gates remain open. A future engine must satisfy the same fd ownership, idempotent stop, bounded await, force-stop and aggregate-metrics contract before it can be selected. The candidate and its gates are recorded in `docs/TUN2PROXY_DEPENDENCY_REVIEW.md`, `docs/CARGO_LOCK_REVIEW.md`, and `docs/DISTRIBUTION.md`.

```text
Android applications
        │ IP packets
        ▼
VpnService / TUN ── app policy + LAN policy
        │
        ▼
TunnelEngine
   ├── current: reviewed hev SOCKS5 engine
   └── target: reviewed multi-protocol engine
          ├── SOCKS5 CONNECT
          └── HTTP proxy + HTTPS destination CONNECT
                         │
                         ▼
              Charles / Reqable / compatible tool
```

The formal user-facing product name changes immediately, but internal `ModernSocks` Kotlin packages, database name, JNI library and migration identifiers remain stable until a rename has a concrete technical benefit. This avoids destructive data/package migration while the network core changes.

The target domain adds `ProxyProtocol.SOCKS5` and `ProxyProtocol.HTTP`. HTTP is presented as “HTTP/HTTPS debug proxy” because ordinary HTTP uses proxy request semantics and HTTPS destinations use the HTTP `CONNECT` method. TunnelLens does not terminate destination TLS; CA trust and certificate pinning remain between the target app and desktop debugging tool.

The existing `SocksVpnService` and native adapter remain the active SOCKS5 implementation. R1 will make profiles protocol-aware, and R2 will introduce a narrow `TunnelEngine` boundary only when a reviewed multi-protocol core is ready. The candidate must preserve the current generation-safe state machine, pre-TUN protocol probe, protected post-TUN sockets, bounded stop, fd ownership, aggregate metrics, per-app routing, and LAN-route behavior.

## R0 application shell

Compact top-level destinations use a Material 3 top app bar: the left menu button opens a navigation drawer and the right overflow menu exposes secondary navigation. Home, Profiles, and Settings remain the only top-level routes. Large screens retain an always-visible navigation rail so the same destinations remain reachable without stretching compact controls.

One stable `NavHost` owns both top-level and nested destinations. Top-level changes use a short fade and subtle scale; forward navigation to an editor uses a restrained leftward motion, while navigate-up/system back uses the matching reverse motion. Nested editors use an auto-mirrored back icon and the same `navigateUp()` path as the system back action. Profile edits track changes against the loaded draft and require explicit discard confirmation before either back path can leave with unsaved data. Drawer selection closes the drawer before starting the destination transition.

## Phase 5.2 local-network bypass

The selected profile's `bypassLan` value is carried through `ConnectionProfile` to the Service without adding a new persistence format. When disabled, the Builder receives `0.0.0.0/0`. When enabled, `VpnIpv4RoutePolicy` computes the complement of `10/8`, `127/8`, `169.254/16`, `172.16/12`, `192.168/16`, `224/4`, and `255.255.255.255/32`. The resulting canonical route set sends every other IPv4 destination to the TUN while those local classes use the underlying network.

The complement strategy is used on all supported API levels, so behavior is identical on API 26–32 and API 33+ and does not depend on the newer `excludeRoute()` API. It composes with per-app allow/bypass policy in the same Builder and does not call `allowBypass()`. Protected upstream SOCKS sockets remain outside the TUN independently of this user policy. IPv6 addresses, routes, and VPN DNS are still absent, so Android blocks the unconfigured IPv6 family by default rather than letting it bypass the VPN. UDP remains rejected at the Phase 3 native ingress boundary.

## Phase 5.1 per-app VPN routing

Each Room profile owns one `AppRoutingPolicy`: `ALL_APPS`, `ONLY_SELECTED`, or `BYPASS_SELECTED`, plus a bounded, validated set of package names. Database migration 1→2 gives existing profiles the safe `ALL_APPS` default. Ordinary profile edits preserve the routing columns; duplication copies them. Import/export deliberately omits installed-package selections because they are device-specific and can reveal the user's application inventory.

The application-routing screen loads launchable apps through a manifest package-visibility query, without requesting `QUERY_ALL_PACKAGES`. It supports label/package search, an optional system-app view, select-visible, clear, and accessible checkbox rows. Consequently, non-launchable packages are not offered in this first capability.

Before TUN creation the Service resolves stored package names against currently installed apps. `ONLY_SELECTED` with no remaining installed package is a deterministic, fail-closed error; it never silently falls back to all applications. `VpnService.Builder` receives either `addAllowedApplication()` calls or `addDisallowedApplication()` calls, never both. Missing bypass packages are ignored safely. UDP, VPN DNS, and IPv6 remain disabled.

## Phase 4 reliability and observability

`SocksVpnService` registers a `ConnectivityManager.NetworkCallback` restricted to Internet-capable, non-VPN networks. Loss of the final underlying network moves the generation-scoped state machine to `Reconnecting`; availability permits the next bounded attempt. Proxy reachability and SOCKS5 authentication/protocol behavior are checked before TUN establishment and periodically while connected. The pre-TUN probe uses the ordinary underlying route and deliberately does not call `VpnService.protect()`; no VPN route exists yet. Connected health probes and every native upstream socket still use `protect()` to prevent tunnel recursion. Two consecutive reachability failures trigger recovery, while authentication and protocol failures are terminal and do not retry.

Reconnect delays are fixed and bounded at 1, 2, 5, 10, and 30 seconds. Both the selected profile and the ordinary DataStore preference must allow automatic reconnection. A user stop cancels the retry job before entering the same idempotent native/TUN cleanup path used by revoke, error, and destruction. Connection-state and metric writes carry the active generation, so an obsolete attempt cannot overwrite a newer session.

hev packet/byte counters are read through the narrow JNI bridge once per second. The Service accumulates upload/download bytes across reconnects and derives connected duration from monotonic elapsed time. Only aggregate duration and byte counts are exposed through `ConnectionMetrics`; destinations, packet content, credentials, and native configuration are neither stored nor logged.

## Phase 3 VPN and native boundary

`HomeRoute` uses Activity Result contracts for notification permission and Android's VPN consent UI. Results are sent to `HomeViewModel`, then to `AndroidVpnController`; Compose never starts or binds the Service directly. `AndroidVpnController` converts user operations into generation-scoped commands and publishes the process-level `ConnectionState` held by `VpnConnectionStateStore`.

```text
HomeScreen -> HomeViewModel -> AndroidVpnController -> SocksVpnService
                                |                    |-- foreground notification
                                |                    |-- IPv4 default-route TUN
                                v                    |-- NativeTunnel JNI
                       VpnConnectionStateStore       |       `-- hev-socks5-tunnel
                                ^                    `-- idempotent cleanup
                                `-----------------------------'
```

`SocksVpnService` owns the Android `ParcelFileDescriptor` and gives native a duplicated fd which native alone closes. It establishes `198.18.0.1/32` plus `0.0.0.0/0`, without DNS or IPv6 routes. The selected profile is decrypted only during connection setup; an escaped in-memory YAML buffer is passed to JNI and cleared after native copies it. A short-lived JVM-attached helper pthread invokes `VpnService.protect(fd)` for each upstream socket and signals completion over a nonblocking pipe consumed by hev cooperative I/O, so the task stack never calls Java and the scheduler pthread is not blocked.

The JNI runtime is process-global and serialized to one session. Native exit stays observable to concurrent Service waiters, stop is bounded, and the timeout fallback shuts down tracked protected sockets and closes native's TUN duplicate. Stop, notification action, revoke, destroy, cancellation, and failure still converge on one generation-checked cleanup path. Non-IPv4/TCP packets are rejected before lwIP and UDP session creation is also rejected at link time; IPv6, DNS, per-app policy, reconnect, and statistics are not claimed. `Disconnected` is published only from Service destruction, preventing a new generation from racing old teardown.

Every connection attempt receives a monotonically increasing generation. The state store rejects stale generations and illegal transitions, preventing an older Service command or coroutine from overwriting the current attempt. The complete contract and ownership rules are recorded in `docs/VPN_LIFECYCLE.md`.

## Phase 1 data and UI structure

TunnelLens remains one Android application module with one `ComponentActivity` and Compose-only UI. The internal `ModernSocksApplication` class owns a small manual application container; Hilt and extra modules are intentionally not used.

```text
MainActivity
  -> ModernSocksApplication / AppContainer
  -> ModernSocksApp
       -> SettingsViewModel -> SettingsRepository -> Preferences DataStore
                            -> AppLanguageController -> AppCompat per-app locales
       -> Navigation Compose
            -> HomeViewModel -> ProfileRepository + VpnController
            -> ProfilesViewModel -> ProfileRepository + ProfileTransferManager
            -> ProfileEditViewModel -> ProfileRepository + SettingsRepository
            -> SettingsScreen

ProfileRepository
  -> RoomProfileRepository
       -> Room DAO / modernsocks.db
       -> CredentialStore -> Android Keystore AES-GCM
```

Compact top-level screens use the navigation drawer and large screens use a navigation rail from 600 dp. Editors are non-top-level destinations, so neither navigation control competes with their back and save actions.

## State and event flow

Each route obtains a ViewModel and collects a single immutable state with `collectAsStateWithLifecycle()`. Screen composables receive state and callbacks only.

- Home combines `StateFlow<ConnectionState>` with the Room-selected profile Flow. The fake connection action is disabled until a profile exists.
- Profiles combines the Room profile list with explicit transient state for delete undo, transfer completion, and operation failure. Snackbar results are consumed back into the ViewModel.
- Profile Edit owns a `ProfileDraft`, field-error map, loading/saving flags, and advanced-section state. It does not use `SavedStateHandle`, so credentials are not persisted into navigation or saved-instance state.
- Settings exposes the DataStore settings Flow. Theme mode and dynamic color are collected above the Material theme, so changes apply immediately.
- Language selection is kept by the Android/AppCompat per-app locale APIs rather than duplicated in DataStore. Changing language triggers the standard Activity configuration recreation, after which Compose resolves every string resource in the selected locale.

Navigation remains in the UI layer. No ViewModel holds a `NavController`, Activity, Service, View, or JNI object.

## Data model and persistence

`ProxyProfileEntity` is Room schema version 2. It stores normalized profile fields, one selected-profile flag, timestamps, an optional encrypted credential envelope, and the per-app routing policy added by migration 1→2. DAO list and selected-profile queries return Flow. Repository transactions ensure the first profile becomes selected, only one final selection is visible, and deleting/restoring a selected profile chooses and restores a valid fallback.

The public `ProxyProfile` model exposes only `hasStoredCredentials`; it never exposes ciphertext or decrypted values. `ProfileDraft` is used only by the editor. Existing passwords are never loaded into UI state: an empty editor password retains the protected value, while a non-empty value replaces it.

Preferences DataStore stores theme mode, dynamic-color preference, future auto-connect preference, and whether advanced editor settings open by default. These settings survive process recreation and are separate from profile data.

## Validation and transfer

`ProfileValidator` trims names and hosts, accepts syntactically valid domains, IPv4, and IPv6, and validates ports 1–65535, MTU 1280–9000, optional DNS, and authentication fields. Invalid imported documents are completely parsed and validated before any profile is written.

Import/export uses a versioned kotlinx.serialization JSON document through the Storage Access Framework. Export deliberately omits usernames, passwords, encrypted blobs, authentication state, IDs, selection, and timestamps. Import has a 1 MiB and 1,000-profile limit, rejects unknown/malformed structure, validates every profile, and requires no storage permission.

## Security and phase boundary

Credential details are documented in `docs/SECURITY.md`. The Room database is excluded from cloud backup and device transfer because Android Keystore keys are device/app-install bound; restoring ciphertext without its key would make credentials unusable. Ordinary DataStore settings may be backed up.

Phase 4 declares Internet, network-state observation, VPN foreground-service, and notification permissions. `AndroidVpnController` is used in production while `FakeVpnController` remains a deterministic test double. The native source/provenance and phase boundary are recorded in `docs/NATIVE_DEPENDENCY_REVIEW.md`; UDP, IPv6 forwarding, and remote DNS remain disabled. Per-app routing and IPv4 LAN bypass are independently implemented at the Android Builder boundary.

English is the unqualified/default resource locale and Simplified Chinese is provided in `values-zh-rCN`. AGP generates a LocaleConfig containing `en` and `zh-CN`, which integrates with Android 13+ system app-language settings. The Settings screen exposes the same System/简体中文/English choice through `AppLanguageController`; AppCompat persists it compatibly on Android 12 and earlier.
