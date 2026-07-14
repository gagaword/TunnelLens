package com.gagaworld.modernsocks.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreCredentialStoreTest {
    private val store = AndroidKeystoreCredentialStore()

    @Test
    fun encryptDecrypt_roundTripsAndUsesFreshIv() = runTest {
        val credentials = Credentials("test-user", "test-password")

        val first = store.encrypt(credentials)
        val second = store.encrypt(credentials)
        val decrypted = store.decrypt(first)

        assertEquals("test-user", decrypted.username)
        assertEquals("test-password", decrypted.password)
        assertFalse(first.iv.contentEquals(second.iv))
        assertFalse(first.ciphertext.contentEquals(second.ciphertext))
        assertNotEquals("test-password", first.ciphertext.toString(Charsets.UTF_8))
    }

    @Test
    fun modifiedCiphertext_failsAuthentication() = runTest {
        val encrypted = store.encrypt(Credentials("user", "password"))
        val modified = encrypted.ciphertext.copyOf().also { bytes ->
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        }

        assertThrows(CredentialStoreException::class.java) {
            kotlinx.coroutines.runBlocking {
                store.decrypt(
                    EncryptedCredentials(encrypted.formatVersion, encrypted.iv, modified),
                )
            }
        }
    }
}
