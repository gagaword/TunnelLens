package com.gagaworld.modernsocks.security

class Credentials(
    val username: String,
    val password: String,
) {
    override fun toString(): String = "Credentials([REDACTED])"
}

class EncryptedCredentials(
    val formatVersion: Int,
    val iv: ByteArray,
    val ciphertext: ByteArray,
) {
    override fun toString(): String =
        "EncryptedCredentials(formatVersion=$formatVersion, iv=[REDACTED], ciphertext=[REDACTED])"
}

interface CredentialStore {
    suspend fun encrypt(credentials: Credentials): EncryptedCredentials

    suspend fun decrypt(credentials: EncryptedCredentials): Credentials
}
