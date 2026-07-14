package com.gagaworld.modernsocks.data.repository

import com.gagaworld.modernsocks.data.model.ProfileDraft
import com.gagaworld.modernsocks.data.model.ProxyProfile
import com.gagaworld.modernsocks.data.model.ValidatedProfile
import com.gagaworld.modernsocks.data.model.AppRoutingMode
import com.gagaworld.modernsocks.data.model.AppRoutingPolicy
import com.gagaworld.modernsocks.data.local.ProxyProfileEntity
import com.gagaworld.modernsocks.security.Credentials
import kotlinx.coroutines.flow.Flow

class DeletedProfile internal constructor(
    val id: Long,
    val name: String,
    internal val entity: ProxyProfileEntity,
)

class ConnectionProfile(
    val id: Long,
    val host: String,
    val port: Int,
    val mtu: Int,
    val autoReconnect: Boolean,
    val bypassLan: Boolean = true,
    val appRoutingPolicy: AppRoutingPolicy = AppRoutingPolicy(),
    val credentials: Credentials? = null,
) {
    override fun toString(): String =
        "ConnectionProfile(id=$id, host=$host, port=$port, mtu=$mtu, " +
            "bypassLan=$bypassLan, " +
            "autoReconnect=$autoReconnect, appRoutingMode=${appRoutingPolicy.mode}, " +
            "appRoutingPackageCount=${appRoutingPolicy.packages.size}, " +
            "credentials=[REDACTED])"
}

interface ProfileRepository {
    val profiles: Flow<List<ProxyProfile>>
    val selectedProfile: Flow<ProxyProfile?>

    suspend fun getDraft(id: Long): ProfileDraft?

    suspend fun getSelectedConnectionProfile(): ConnectionProfile?

    suspend fun save(profile: ValidatedProfile): Long

    suspend fun duplicate(id: Long, newName: String): Long

    suspend fun delete(id: Long): DeletedProfile?

    suspend fun restore(profile: DeletedProfile)

    suspend fun select(id: Long)

    suspend fun getAppRouting(id: Long): AppRoutingPolicy?

    suspend fun updateAppRouting(id: Long, mode: AppRoutingMode, packages: Set<String>)
}
