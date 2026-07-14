package com.gagaworld.modernsocks.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "proxy_profiles",
    indices = [Index(value = ["isSelected"])],
)
data class ProxyProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int,
    val authenticationEnabled: Boolean,
    val credentialFormatVersion: Int?,
    val credentialIv: ByteArray?,
    val credentialCiphertext: ByteArray?,
    val dnsServer: String?,
    val mtu: Int,
    val bypassLan: Boolean,
    val autoReconnect: Boolean,
    val appRoutingMode: String = "ALL_APPS",
    val appRoutingPackages: String = "[]",
    val isSelected: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
