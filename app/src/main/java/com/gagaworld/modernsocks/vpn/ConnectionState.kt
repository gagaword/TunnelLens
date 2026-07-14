package com.gagaworld.modernsocks.vpn

data class ConnectionMetrics(
    val connectedDurationMillis: Long = 0,
    val uploadedBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val reconnectAttempt: Int = 0,
)

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object PreparingPermission : ConnectionState
    data object Starting : ConnectionState
    data object Connected : ConnectionState
    data object Reconnecting : ConnectionState
    data object Stopping : ConnectionState
    data class Error(val reason: ConnectionFailure) : ConnectionState
}

enum class ConnectionFailure {
    PERMISSION_DENIED,
    NO_PROFILE,
    SERVICE_START_FAILED,
    SERVICE_STOP_FAILED,
    TUN_ESTABLISH_FAILED,
    NATIVE_START_FAILED,
    NATIVE_EXITED,
    NATIVE_STOP_TIMEOUT,
    NETWORK_UNAVAILABLE,
    PROXY_UNREACHABLE,
    AUTHENTICATION_FAILED,
    SOCKS_PROTOCOL_ERROR,
    RECONNECT_EXHAUSTED,
    APP_ROUTING_INVALID,
    PERMISSION_REVOKED,
    UNEXPECTED,
}
