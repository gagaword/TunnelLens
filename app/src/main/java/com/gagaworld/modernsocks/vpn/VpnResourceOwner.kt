package com.gagaworld.modernsocks.vpn

import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference

class VpnResourceOwner {
    private val resource = AtomicReference<Closeable?>(null)

    fun attach(value: Closeable): Boolean {
        if (resource.compareAndSet(null, value)) return true
        runCatching { value.close() }
        return false
    }

    fun close(): Boolean {
        val value = resource.getAndSet(null) ?: return false
        runCatching { value.close() }
        return true
    }

    fun hasResource(): Boolean = resource.get() != null
}
