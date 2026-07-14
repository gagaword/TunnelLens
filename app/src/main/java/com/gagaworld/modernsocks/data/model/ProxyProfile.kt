package com.gagaworld.modernsocks.data.model

data class ProxyProfile(
    val id: Long,
    val name: String,
    val host: String,
    val port: Int,
    val authenticationEnabled: Boolean,
    val hasStoredCredentials: Boolean,
    val dnsServer: String?,
    val mtu: Int,
    val bypassLan: Boolean,
    val autoReconnect: Boolean,
    val appRoutingMode: AppRoutingMode = AppRoutingMode.ALL_APPS,
    val appRoutingPackageCount: Int = 0,
    val isSelected: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

val ProxyProfile.endpoint: String
    get() = if (host.contains(':')) "[$host]:$port" else "$host:$port"
