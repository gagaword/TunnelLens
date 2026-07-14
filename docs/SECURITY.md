# Credential Security

## Stored format

Proxy usernames and passwords are encoded together into a bounded, length-prefixed UTF-8 payload and encrypted before Room receives them. Room stores only:

- credential format version;
- random GCM initialization vector;
- authenticated ciphertext including the GCM tag.

The profile list model contains only a `hasStoredCredentials` flag. `toString()` for credential objects and drafts redacts credential values.

## Key and encryption

`AndroidKeystoreCredentialStore` creates an app-private, non-exportable 256-bit AES key in the `AndroidKeyStore` provider. The key is authorized only for encryption and decryption with GCM and no padding. Encryption initializes `Cipher` without a caller-provided IV, so Android Keystore enforces randomized encryption and the provider supplies a fresh 12-byte IV. A fixed format identifier is authenticated as additional data, and decryption rejects an incorrect version, IV length, malformed payload, or GCM tag.

Keystore and cipher operations run on `Dispatchers.IO`. Temporary byte arrays are cleared after use where the platform representation permits it. Kotlin/Java strings cannot be reliably zeroized; therefore decrypted values are kept out of logs, Bundles, navigation arguments, `SavedStateHandle`, clipboard, exports, and profile-list state, and are held only for the shortest repository/editor operation possible.

## Editing behavior

Opening an authenticated profile decrypts the username for editing but does not reveal the existing password. The password field remains empty and indicates that a protected value exists. Saving an empty password retains the old password inside the repository; entering a new password replaces the envelope with a newly randomized encryption result. Disabling authentication removes all credential-envelope columns.

## Import, export, and backup

JSON export never contains username, password, authentication state, IV, ciphertext, database ID, or timestamps. Imported profiles start without authentication and pass the same validation used by the editor.

The Room database is excluded from Auto Backup and device-to-device transfer. Android Keystore key material is not exported with the database, so excluding the database prevents restoration of undecryptable credential envelopes. Ordinary theme and behavior preferences remain eligible for platform backup.

## VPN and native boundary

SOCKS5 is a proxy protocol and does not itself encrypt traffic between the device and proxy server. Phase 3 requests the selected encrypted envelope only after VPN startup, decrypts it on the repository IO path, and keeps the resulting username/password out of UI state, bundles, notifications, and logs. The escaped native YAML exists only in bounded memory: the JNI-owned byte copy is explicitly cleared after the blocking core exits and the Kotlin byte array is cleared immediately after `start()` returns. No temporary configuration file or native configuration log is produced. JVM strings cannot be reliably zeroized, so their scope and lifetime are minimized.

The native socket protector receives only an integer fd. It runs on a dedicated JVM-attached pthread, never records destinations or authentication data, and fails closed if Android rejects protection. Native logging is set to error level and `/dev/null`; the full configuration and credentials are never logged.

References:

- <https://developer.android.com/privacy-and-security/keystore>
- <https://developer.android.com/privacy-and-security/cryptography>
- <https://developer.android.com/identity/data/autobackup>
