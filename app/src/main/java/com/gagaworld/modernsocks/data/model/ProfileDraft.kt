package com.gagaworld.modernsocks.data.model

data class ProfileDraft(
    val id: Long? = null,
    val name: String = "",
    val host: String = "",
    val port: String = "1080",
    val authenticationEnabled: Boolean = false,
    val username: String = "",
    val password: String = "",
    val hasStoredPassword: Boolean = false,
    val dnsServer: String = "",
    val mtu: String = "1500",
    val bypassLan: Boolean = true,
    val autoReconnect: Boolean = true,
) {
    override fun toString(): String =
        "ProfileDraft(id=$id, name=$name, host=$host, port=$port, " +
            "authenticationEnabled=$authenticationEnabled, username=[REDACTED], " +
            "password=[REDACTED], hasStoredPassword=$hasStoredPassword, " +
            "dnsServer=$dnsServer, mtu=$mtu, bypassLan=$bypassLan, " +
            "autoReconnect=$autoReconnect)"
}
