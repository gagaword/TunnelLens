package com.gagaworld.modernsocks.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppRoutingCodecTest {
    @Test
    fun codecSortsDeduplicatesAndRejectsInvalidPackages() {
        val encoded = AppRoutingCodec.encode(
            setOf("com.example.z", "com.example.a", "not a package"),
        )

        assertEquals(setOf("com.example.a", "com.example.z"), AppRoutingCodec.decode(encoded))
        assertFalse(encoded.contains("not a package"))
    }

    @Test
    fun onlySelectedWithoutPackagesIsUnsafe() {
        assertFalse(AppRoutingPolicy(AppRoutingMode.ONLY_SELECTED).isSafe)
    }
}
