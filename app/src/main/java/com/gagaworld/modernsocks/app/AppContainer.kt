package com.gagaworld.modernsocks.app

import android.content.Context
import com.gagaworld.modernsocks.data.local.ModernSocksDatabase
import com.gagaworld.modernsocks.data.preferences.DataStoreSettingsRepository
import com.gagaworld.modernsocks.data.preferences.SettingsRepository
import com.gagaworld.modernsocks.data.repository.ProfileRepository
import com.gagaworld.modernsocks.data.repository.RoomProfileRepository
import com.gagaworld.modernsocks.security.AndroidKeystoreCredentialStore
import com.gagaworld.modernsocks.vpn.VpnController
import com.gagaworld.modernsocks.vpn.AndroidVpnController
import com.gagaworld.modernsocks.vpn.AndroidVpnPreparation
import com.gagaworld.modernsocks.vpn.AndroidVpnServiceCommands
import com.gagaworld.modernsocks.vpn.VpnConnectionStateStore
import com.gagaworld.modernsocks.data.transfer.ContentResolverProfileTransferManager
import com.gagaworld.modernsocks.data.transfer.ProfileTransferManager
import com.gagaworld.modernsocks.data.locale.AndroidAppLanguageController
import com.gagaworld.modernsocks.data.locale.AppLanguageController
import com.gagaworld.modernsocks.data.apps.AndroidInstalledAppRepository
import com.gagaworld.modernsocks.data.apps.InstalledAppRepository
import com.gagaworld.modernsocks.tunnel.HevSocks5TunnelEngine
import com.gagaworld.modernsocks.tunnel.TunnelEngine

interface AppContainer {
    val profileRepository: ProfileRepository
    val settingsRepository: SettingsRepository
    val vpnController: VpnController
    val profileTransferManager: ProfileTransferManager
    val appLanguageController: AppLanguageController
    val vpnStateStore: VpnConnectionStateStore
    val installedAppRepository: InstalledAppRepository
    val tunnelEngine: TunnelEngine
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val database: ModernSocksDatabase by lazy {
        ModernSocksDatabase.create(context)
    }
    private val credentialStore by lazy {
        AndroidKeystoreCredentialStore()
    }

    override val profileRepository: ProfileRepository by lazy {
        RoomProfileRepository(database, credentialStore)
    }
    override val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(context)
    }
    override val vpnStateStore: VpnConnectionStateStore by lazy {
        VpnConnectionStateStore()
    }
    override val vpnController: VpnController by lazy {
        AndroidVpnController(
            stateStore = vpnStateStore,
            preparation = AndroidVpnPreparation(context),
            serviceCommands = AndroidVpnServiceCommands(context),
        )
    }
    override val profileTransferManager: ProfileTransferManager by lazy {
        ContentResolverProfileTransferManager(
            contentResolver = context.contentResolver,
            repository = profileRepository,
        )
    }
    override val appLanguageController: AppLanguageController by lazy {
        AndroidAppLanguageController()
    }
    override val installedAppRepository: InstalledAppRepository by lazy {
        AndroidInstalledAppRepository(context)
    }
    override val tunnelEngine: TunnelEngine by lazy {
        HevSocks5TunnelEngine()
    }
}
