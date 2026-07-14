package com.gagaworld.modernsocks.vpn

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VpnManifestTest {
    @Test
    fun vpnServiceAndForegroundPermissionsAreDeclared() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        @Suppress("DEPRECATION")
        val service = packageManager.getServiceInfo(
            ComponentName(context, SocksVpnService::class.java),
            PackageManager.GET_META_DATA,
        )
        @Suppress("DEPRECATION")
        val requestedPermissions = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        ).requestedPermissions.orEmpty().toSet()

        assertTrue(service.exported)
        assertEquals(Manifest.permission.BIND_VPN_SERVICE, service.permission)
        assertEquals(false, service.metaData.getBoolean("android.net.VpnService.SUPPORTS_ALWAYS_ON"))
        assertTrue(Manifest.permission.FOREGROUND_SERVICE in requestedPermissions)
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in requestedPermissions)
        assertTrue(
            "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED" in requestedPermissions,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            assertTrue(
                service.foregroundServiceType and
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED != 0,
            )
        }
    }
}
