package com.gagaworld.modernsocks

import com.gagaworld.modernsocks.data.local.ProxyProfileEntity
import com.gagaworld.modernsocks.data.model.ProfileDraft
import com.gagaworld.modernsocks.data.model.ProxyProfile
import com.gagaworld.modernsocks.data.model.ValidatedProfile
import com.gagaworld.modernsocks.data.preferences.AppSettings
import com.gagaworld.modernsocks.data.preferences.SettingsRepository
import com.gagaworld.modernsocks.data.preferences.ThemeMode
import com.gagaworld.modernsocks.data.repository.DeletedProfile
import com.gagaworld.modernsocks.data.repository.ConnectionProfile
import com.gagaworld.modernsocks.data.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.gagaworld.modernsocks.data.locale.AppLanguage
import com.gagaworld.modernsocks.data.locale.AppLanguageController
import com.gagaworld.modernsocks.data.model.AppRoutingMode
import com.gagaworld.modernsocks.data.model.AppRoutingPolicy

class FakeProfileRepository(
    initialProfiles: List<ProxyProfile> = emptyList(),
) : ProfileRepository {
    private val profilesFlow = MutableStateFlow(initialProfiles)
    private val selectedFlow = MutableStateFlow(initialProfiles.firstOrNull { it.isSelected })
    var draft: ProfileDraft? = null
    var savedProfile: ValidatedProfile? = null
    var appRoutingPolicy = AppRoutingPolicy()

    override val profiles: Flow<List<ProxyProfile>> = profilesFlow
    override val selectedProfile: Flow<ProxyProfile?> = selectedFlow

    override suspend fun getDraft(id: Long): ProfileDraft? = draft

    override suspend fun getSelectedConnectionProfile(): ConnectionProfile? =
        selectedFlow.value?.let { profile ->
            ConnectionProfile(
                id = profile.id,
                host = profile.host,
                port = profile.port,
                mtu = profile.mtu,
                bypassLan = profile.bypassLan,
                autoReconnect = profile.autoReconnect,
                credentials = null,
            )
        }

    override suspend fun save(profile: ValidatedProfile): Long {
        savedProfile = profile
        return profile.id ?: 1L
    }

    override suspend fun duplicate(id: Long, newName: String): Long = id + 100

    override suspend fun delete(id: Long): DeletedProfile? {
        val profile = profilesFlow.value.firstOrNull { it.id == id } ?: return null
        profilesFlow.value = profilesFlow.value.filterNot { it.id == id }
        selectedFlow.value = profilesFlow.value.firstOrNull { it.isSelected }
        return DeletedProfile(
            id = profile.id,
            name = profile.name,
            entity = ProxyProfileEntity(
                id = profile.id,
                name = profile.name,
                host = profile.host,
                port = profile.port,
                authenticationEnabled = false,
                credentialFormatVersion = null,
                credentialIv = null,
                credentialCiphertext = null,
                dnsServer = profile.dnsServer,
                mtu = profile.mtu,
                bypassLan = profile.bypassLan,
                autoReconnect = profile.autoReconnect,
                isSelected = profile.isSelected,
                createdAt = profile.createdAt,
                updatedAt = profile.updatedAt,
            ),
        )
    }

    override suspend fun restore(profile: DeletedProfile) {
        val entity = profile.entity
        val restored = ProxyProfile(
            id = entity.id,
            name = entity.name,
            host = entity.host,
            port = entity.port,
            authenticationEnabled = entity.authenticationEnabled,
            hasStoredCredentials = false,
            dnsServer = entity.dnsServer,
            mtu = entity.mtu,
            bypassLan = entity.bypassLan,
            autoReconnect = entity.autoReconnect,
            isSelected = entity.isSelected,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
        profilesFlow.value = profilesFlow.value + restored
        if (restored.isSelected) selectedFlow.value = restored
    }

    override suspend fun select(id: Long) {
        profilesFlow.value = profilesFlow.value.map { it.copy(isSelected = it.id == id) }
        selectedFlow.value = profilesFlow.value.firstOrNull { it.id == id }
    }

    override suspend fun getAppRouting(id: Long): AppRoutingPolicy? =
        profilesFlow.value.firstOrNull { it.id == id }?.let { appRoutingPolicy }

    override suspend fun updateAppRouting(
        id: Long,
        mode: AppRoutingMode,
        packages: Set<String>,
    ) {
        appRoutingPolicy = AppRoutingPolicy(mode, packages)
        profilesFlow.value = profilesFlow.value.map {
            if (it.id == id) it.copy(
                appRoutingMode = mode,
                appRoutingPackageCount = packages.size,
            ) else it
        }
    }
}

class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {
    val mutableSettings = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = mutableSettings

    override suspend fun setThemeMode(mode: ThemeMode) {
        mutableSettings.value = mutableSettings.value.copy(themeMode = mode)
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        mutableSettings.value = mutableSettings.value.copy(dynamicColor = enabled)
    }

    override suspend fun setAutoConnect(enabled: Boolean) {
        mutableSettings.value = mutableSettings.value.copy(autoConnect = enabled)
    }

    override suspend fun setExpandAdvancedByDefault(enabled: Boolean) {
        mutableSettings.value = mutableSettings.value.copy(expandAdvancedByDefault = enabled)
    }
}

class FakeAppLanguageController(
    initial: AppLanguage = AppLanguage.SYSTEM,
) : AppLanguageController {
    var language: AppLanguage = initial
        private set

    override fun currentLanguage(): AppLanguage = language

    override fun setLanguage(language: AppLanguage) {
        this.language = language
    }
}

fun testProfile(isSelected: Boolean = true): ProxyProfile = ProxyProfile(
    id = 1,
    name = "Test profile",
    host = "example.invalid",
    port = 1080,
    authenticationEnabled = false,
    hasStoredCredentials = false,
    dnsServer = null,
    mtu = 1500,
    bypassLan = true,
    autoReconnect = true,
    isSelected = isSelected,
    createdAt = 1,
    updatedAt = 1,
)
