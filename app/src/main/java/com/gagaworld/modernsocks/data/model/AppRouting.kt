package com.gagaworld.modernsocks.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class AppRoutingMode {
    ALL_APPS,
    ONLY_SELECTED,
    BYPASS_SELECTED,
}

data class AppRoutingPolicy(
    val mode: AppRoutingMode = AppRoutingMode.ALL_APPS,
    val packages: Set<String> = emptySet(),
) {
    val isSafe: Boolean
        get() = mode != AppRoutingMode.ONLY_SELECTED || packages.isNotEmpty()
}

object AppRoutingCodec {
    private val json = Json { ignoreUnknownKeys = false }
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

    fun encode(packages: Set<String>): String = json.encodeToString(
        packages.filter(::isValidPackage).sorted(),
    )

    fun decode(value: String): Set<String> = runCatching {
        json.decodeFromString<List<String>>(value)
            .asSequence()
            .filter(::isValidPackage)
            .take(1_000)
            .toSet()
    }.getOrDefault(emptySet())

    fun isValidPackage(value: String): Boolean =
        value.length <= 255 && packagePattern.matches(value)
}
