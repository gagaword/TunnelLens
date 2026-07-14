package com.gagaworld.modernsocks.data.repository

import androidx.room.withTransaction
import com.gagaworld.modernsocks.data.local.ModernSocksDatabase
import com.gagaworld.modernsocks.data.local.ProxyProfileEntity
import com.gagaworld.modernsocks.data.model.ProfileDraft
import com.gagaworld.modernsocks.data.model.ProxyProfile
import com.gagaworld.modernsocks.data.model.ValidatedProfile
import com.gagaworld.modernsocks.data.model.AppRoutingCodec
import com.gagaworld.modernsocks.data.model.AppRoutingMode
import com.gagaworld.modernsocks.data.model.AppRoutingPolicy
import com.gagaworld.modernsocks.security.CredentialStore
import com.gagaworld.modernsocks.security.Credentials
import com.gagaworld.modernsocks.security.EncryptedCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProfileRepositoryException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class RoomProfileRepository(
    private val database: ModernSocksDatabase,
    private val credentialStore: CredentialStore,
    private val now: () -> Long = System::currentTimeMillis,
) : ProfileRepository {
    private val dao = database.proxyProfileDao()

    override val profiles: Flow<List<ProxyProfile>> = dao.observeAll().map { entities ->
        entities.map(ProxyProfileEntity::toModel)
    }

    override val selectedProfile: Flow<ProxyProfile?> = dao.observeSelected().map { entity ->
        entity?.toModel()
    }

    override suspend fun getDraft(id: Long): ProfileDraft? {
        val entity = dao.getById(id) ?: return null
        val credentials = entity.encryptedCredentials()?.let { encrypted ->
            try {
                credentialStore.decrypt(encrypted)
            } catch (error: Exception) {
                throw ProfileRepositoryException("Stored credentials cannot be opened", error)
            }
        }
        return ProfileDraft(
            id = entity.id,
            name = entity.name,
            host = entity.host,
            port = entity.port.toString(),
            authenticationEnabled = entity.authenticationEnabled,
            username = credentials?.username.orEmpty(),
            password = "",
            hasStoredPassword = credentials != null,
            dnsServer = entity.dnsServer.orEmpty(),
            mtu = entity.mtu.toString(),
            bypassLan = entity.bypassLan,
            autoReconnect = entity.autoReconnect,
        )
    }

    override suspend fun getSelectedConnectionProfile(): ConnectionProfile? {
        val entity = dao.getSelected() ?: return null
        val credentials = entity.encryptedCredentials()?.let { encrypted ->
            try {
                credentialStore.decrypt(encrypted)
            } catch (error: Exception) {
                throw ProfileRepositoryException("Stored credentials cannot be opened", error)
            }
        }
        return ConnectionProfile(
            id = entity.id,
            host = entity.host,
            port = entity.port,
            mtu = entity.mtu,
            bypassLan = entity.bypassLan,
            autoReconnect = entity.autoReconnect,
            appRoutingPolicy = entity.appRoutingPolicy(),
            credentials = credentials,
        )
    }

    override suspend fun save(profile: ValidatedProfile): Long {
        val existing = profile.id?.let { dao.getById(it) }
        if (profile.id != null && existing == null) {
            throw ProfileRepositoryException("Profile no longer exists")
        }
        val encrypted = credentialsFor(profile, existing)
        val timestamp = now()

        return database.withTransaction {
            if (existing == null) {
                val selectNewProfile = dao.count() == 0
                val id = dao.insert(
                    profile.toEntity(
                        encrypted = encrypted,
                        createdAt = timestamp,
                        updatedAt = timestamp,
                        isSelected = selectNewProfile,
                    ),
                )
                if (selectNewProfile) {
                    dao.clearSelection()
                    dao.select(id, timestamp)
                }
                id
            } else {
                val updated = profile.toEntity(
                    encrypted = encrypted,
                    createdAt = existing.createdAt,
                    updatedAt = timestamp,
                    isSelected = existing.isSelected,
                ).copy(
                    id = existing.id,
                    appRoutingMode = existing.appRoutingMode,
                    appRoutingPackages = existing.appRoutingPackages,
                )
                if (dao.update(updated) != 1) {
                    throw ProfileRepositoryException("Profile update failed")
                }
                existing.id
            }
        }
    }

    override suspend fun duplicate(id: Long, newName: String): Long = database.withTransaction {
        val source = dao.getById(id) ?: throw ProfileRepositoryException("Profile no longer exists")
        val timestamp = now()
        dao.insert(
            source.copy(
                id = 0,
                name = newName.trim().take(64),
                isSelected = false,
                createdAt = timestamp,
                updatedAt = timestamp,
                credentialIv = source.credentialIv?.copyOf(),
                credentialCiphertext = source.credentialCiphertext?.copyOf(),
            ),
        )
    }

    override suspend fun delete(id: Long): DeletedProfile? = database.withTransaction {
        val entity = dao.getById(id) ?: return@withTransaction null
        dao.delete(entity)
        if (entity.isSelected) {
            dao.getFirst()?.let { replacement ->
                dao.clearSelection()
                dao.select(replacement.id, now())
            }
        }
        DeletedProfile(id = entity.id, name = entity.name, entity = entity.deepCopy())
    }

    override suspend fun restore(profile: DeletedProfile) {
        val entity = profile.entity
        database.withTransaction {
            dao.insert(entity.deepCopy())
            if (entity.isSelected) {
                dao.clearSelection()
                dao.select(entity.id, now())
            }
        }
    }

    override suspend fun select(id: Long) {
        database.withTransaction {
            if (dao.getById(id) == null) throw ProfileRepositoryException("Profile no longer exists")
            dao.clearSelection()
            if (dao.select(id, now()) != 1) throw ProfileRepositoryException("Profile selection failed")
        }
    }

    override suspend fun getAppRouting(id: Long): AppRoutingPolicy? =
        dao.getById(id)?.appRoutingPolicy()

    override suspend fun updateAppRouting(
        id: Long,
        mode: AppRoutingMode,
        packages: Set<String>,
    ) {
        val filtered = packages.filter(AppRoutingCodec::isValidPackage).take(1_000).toSet()
        if (mode == AppRoutingMode.ONLY_SELECTED && filtered.isEmpty()) {
            throw ProfileRepositoryException("At least one application must be selected")
        }
        val existing = dao.getById(id) ?: throw ProfileRepositoryException("Profile no longer exists")
        if (dao.update(
                existing.copy(
                    appRoutingMode = mode.name,
                    appRoutingPackages = AppRoutingCodec.encode(filtered),
                    updatedAt = now(),
                ),
            ) != 1
        ) {
            throw ProfileRepositoryException("Application routing update failed")
        }
    }

    private suspend fun credentialsFor(
        profile: ValidatedProfile,
        existing: ProxyProfileEntity?,
    ): EncryptedCredentials? {
        if (!profile.authenticationEnabled) return null
        val password = when {
            profile.password.isNotEmpty() -> profile.password
            profile.hasStoredPassword -> {
                val encrypted = existing?.encryptedCredentials()
                    ?: throw ProfileRepositoryException("Stored credentials are missing")
                try {
                    credentialStore.decrypt(encrypted).password
                } catch (error: Exception) {
                    throw ProfileRepositoryException("Stored credentials cannot be opened", error)
                }
            }
            else -> throw ProfileRepositoryException("Password is required")
        }
        return try {
            credentialStore.encrypt(Credentials(profile.username, password))
        } catch (error: Exception) {
            throw ProfileRepositoryException("Credentials cannot be protected", error)
        }
    }
}

private fun ProxyProfileEntity.toModel(): ProxyProfile = ProxyProfile(
    id = id,
    name = name,
    host = host,
    port = port,
    authenticationEnabled = authenticationEnabled,
    hasStoredCredentials = encryptedCredentials() != null,
    dnsServer = dnsServer,
    mtu = mtu,
    bypassLan = bypassLan,
    autoReconnect = autoReconnect,
    appRoutingMode = runCatching { AppRoutingMode.valueOf(appRoutingMode) }
        .getOrDefault(AppRoutingMode.ALL_APPS),
    appRoutingPackageCount = AppRoutingCodec.decode(appRoutingPackages).size,
    isSelected = isSelected,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ValidatedProfile.toEntity(
    encrypted: EncryptedCredentials?,
    createdAt: Long,
    updatedAt: Long,
    isSelected: Boolean,
): ProxyProfileEntity = ProxyProfileEntity(
    name = name,
    host = host,
    port = port,
    authenticationEnabled = authenticationEnabled,
    credentialFormatVersion = encrypted?.formatVersion,
    credentialIv = encrypted?.iv?.copyOf(),
    credentialCiphertext = encrypted?.ciphertext?.copyOf(),
    dnsServer = dnsServer,
    mtu = mtu,
    bypassLan = bypassLan,
    autoReconnect = autoReconnect,
    appRoutingMode = AppRoutingMode.ALL_APPS.name,
    appRoutingPackages = "[]",
    isSelected = isSelected,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun ProxyProfileEntity.encryptedCredentials(): EncryptedCredentials? {
    val version = credentialFormatVersion ?: return null
    val iv = credentialIv ?: return null
    val ciphertext = credentialCiphertext ?: return null
    return EncryptedCredentials(version, iv.copyOf(), ciphertext.copyOf())
}

private fun ProxyProfileEntity.deepCopy(): ProxyProfileEntity = copy(
    credentialIv = credentialIv?.copyOf(),
    credentialCiphertext = credentialCiphertext?.copyOf(),
)

private fun ProxyProfileEntity.appRoutingPolicy(): AppRoutingPolicy = AppRoutingPolicy(
    mode = runCatching { AppRoutingMode.valueOf(appRoutingMode) }
        .getOrDefault(AppRoutingMode.ALL_APPS),
    packages = AppRoutingCodec.decode(appRoutingPackages),
)
