package com.gagaworld.modernsocks.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CredentialStoreException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class AndroidKeystoreCredentialStore(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CredentialStore {
    override suspend fun encrypt(credentials: Credentials): EncryptedCredentials =
        withContext(ioDispatcher) {
            val plaintext = encode(credentials)
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
                cipher.updateAAD(ASSOCIATED_DATA)
                EncryptedCredentials(
                    formatVersion = FORMAT_VERSION,
                    iv = cipher.iv.copyOf(),
                    ciphertext = cipher.doFinal(plaintext),
                )
            } catch (error: Exception) {
                throw CredentialStoreException("Credential encryption failed", error)
            } finally {
                plaintext.fill(0)
            }
        }

    override suspend fun decrypt(credentials: EncryptedCredentials): Credentials =
        withContext(ioDispatcher) {
            if (credentials.formatVersion != FORMAT_VERSION || credentials.iv.size != GCM_IV_BYTES) {
                throw CredentialStoreException("Unsupported credential envelope")
            }
            val plaintext = try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    GCMParameterSpec(GCM_TAG_BITS, credentials.iv),
                )
                cipher.updateAAD(ASSOCIATED_DATA)
                cipher.doFinal(credentials.ciphertext)
            } catch (error: AEADBadTagException) {
                throw CredentialStoreException("Credential integrity check failed", error)
            } catch (error: Exception) {
                throw CredentialStoreException("Credential decryption failed", error)
            }
            try {
                decode(plaintext)
            } finally {
                plaintext.fill(0)
            }
        }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val specification = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(specification)
        return keyGenerator.generateKey()
    }

    private fun encode(credentials: Credentials): ByteArray {
        val username = credentials.username.toByteArray(StandardCharsets.UTF_8)
        val password = credentials.password.toByteArray(StandardCharsets.UTF_8)
        require(username.size <= MAX_FIELD_BYTES && password.size <= MAX_FIELD_BYTES)
        return try {
            ByteBuffer.allocate(Int.SIZE_BYTES * 2 + username.size + password.size)
                .putInt(username.size)
                .put(username)
                .putInt(password.size)
                .put(password)
                .array()
        } finally {
            username.fill(0)
            password.fill(0)
        }
    }

    private fun decode(plaintext: ByteArray): Credentials {
        try {
            val buffer = ByteBuffer.wrap(plaintext)
            val usernameSize = buffer.int.checkedFieldSize(buffer.remaining())
            val username = ByteArray(usernameSize).also(buffer::get)
            val passwordSize = buffer.int.checkedFieldSize(buffer.remaining())
            val password = ByteArray(passwordSize).also(buffer::get)
            if (buffer.hasRemaining()) throw CredentialStoreException("Malformed credential payload")
            return try {
                Credentials(
                    username = String(username, StandardCharsets.UTF_8),
                    password = String(password, StandardCharsets.UTF_8),
                )
            } finally {
                username.fill(0)
                password.fill(0)
            }
        } catch (error: CredentialStoreException) {
            throw error
        } catch (error: Exception) {
            throw CredentialStoreException("Malformed credential payload", error)
        }
    }

    private fun Int.checkedFieldSize(remaining: Int): Int {
        if (this < 0 || this > MAX_FIELD_BYTES || this > remaining) {
            throw CredentialStoreException("Malformed credential payload")
        }
        return this
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "modernsocks.profile.credentials.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = 1
        const val GCM_TAG_BITS = 128
        const val GCM_IV_BYTES = 12
        const val MAX_FIELD_BYTES = 4096
        val ASSOCIATED_DATA = "ModernSocks.credentials.v1".toByteArray(StandardCharsets.UTF_8)
    }
}
