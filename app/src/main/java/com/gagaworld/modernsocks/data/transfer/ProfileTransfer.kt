package com.gagaworld.modernsocks.data.transfer

import android.content.ContentResolver
import android.net.Uri
import com.gagaworld.modernsocks.data.model.ProfileDraft
import com.gagaworld.modernsocks.data.model.ProfileValidator
import com.gagaworld.modernsocks.data.model.ProxyProfile
import com.gagaworld.modernsocks.data.model.ValidatedProfile
import com.gagaworld.modernsocks.data.repository.ProfileRepository
import java.nio.charset.StandardCharsets
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProfileTransferException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

object ProfileTransferCodec {
    private const val FORMAT_VERSION = 1
    private const val MAX_PROFILES = 1_000
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    fun encode(profiles: List<ProxyProfile>): String = json.encodeToString(
        ExportBundle(
            formatVersion = FORMAT_VERSION,
            profiles = profiles.map { profile ->
                ExportProfile(
                    name = profile.name,
                    host = profile.host,
                    port = profile.port,
                    dnsServer = profile.dnsServer,
                    mtu = profile.mtu,
                    bypassLan = profile.bypassLan,
                    autoReconnect = profile.autoReconnect,
                )
            },
        ),
    )

    fun decode(content: String): List<ValidatedProfile> {
        val bundle = try {
            json.decodeFromString<ExportBundle>(content)
        } catch (error: SerializationException) {
            throw ProfileTransferException("Invalid configuration document", error)
        }
        if (bundle.formatVersion != FORMAT_VERSION || bundle.profiles.size > MAX_PROFILES) {
            throw ProfileTransferException("Unsupported configuration document")
        }
        return bundle.profiles.map { imported ->
            val validation = ProfileValidator.validate(
                ProfileDraft(
                    name = imported.name,
                    host = imported.host,
                    port = imported.port.toString(),
                    authenticationEnabled = false,
                    dnsServer = imported.dnsServer.orEmpty(),
                    mtu = imported.mtu.toString(),
                    bypassLan = imported.bypassLan,
                    autoReconnect = imported.autoReconnect,
                ),
            )
            validation.profile ?: throw ProfileTransferException("Imported profile is invalid")
        }
    }
}

interface ProfileTransferManager {
    suspend fun exportTo(uri: Uri): Int
    suspend fun importFrom(uri: Uri): Int
}

class ContentResolverProfileTransferManager(
    private val contentResolver: ContentResolver,
    private val repository: ProfileRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ProfileTransferManager {
    override suspend fun exportTo(uri: Uri): Int = withContext(ioDispatcher) {
        val profiles = repository.profiles.first()
        val bytes = ProfileTransferCodec.encode(profiles).toByteArray(StandardCharsets.UTF_8)
        try {
            contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(bytes)
            } ?: throw ProfileTransferException("Cannot open export destination")
            profiles.size
        } catch (error: ProfileTransferException) {
            throw error
        } catch (error: Exception) {
            throw ProfileTransferException("Configuration export failed", error)
        } finally {
            bytes.fill(0)
        }
    }

    override suspend fun importFrom(uri: Uri): Int {
        val content = withContext(ioDispatcher) {
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readLimited(MAX_IMPORT_BYTES)
                    try {
                        String(bytes, StandardCharsets.UTF_8)
                    } finally {
                        bytes.fill(0)
                    }
                } ?: throw ProfileTransferException("Cannot open import document")
            } catch (error: ProfileTransferException) {
                throw error
            } catch (error: Exception) {
                throw ProfileTransferException("Configuration import failed", error)
            }
        }
        val profiles = ProfileTransferCodec.decode(content)
        profiles.forEach { repository.save(it) }
        return profiles.size
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 1_048_576
    }
}

private fun InputStream.readLimited(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maxBytes, 8_192))
    val buffer = ByteArray(8_192)
    var total = 0
    try {
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) {
                throw ProfileTransferException("Configuration document is too large")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    } finally {
        buffer.fill(0)
        output.reset()
    }
}

@Serializable
private data class ExportBundle(
    val formatVersion: Int,
    val profiles: List<ExportProfile>,
)

@Serializable
private data class ExportProfile(
    val name: String,
    val host: String,
    val port: Int,
    val dnsServer: String? = null,
    val mtu: Int = 1500,
    val bypassLan: Boolean = true,
    val autoReconnect: Boolean = true,
)
