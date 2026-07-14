package com.gagaworld.modernsocks.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

fun interface InstalledAppRepository {
    suspend fun loadLaunchableApps(): List<InstalledApp>
}

class AndroidInstalledAppRepository(
    private val context: Context,
) : InstalledAppRepository {
    override suspend fun loadLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        resolved.asSequence()
            .mapNotNull { info ->
                val applicationInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
                val packageName = applicationInfo.packageName
                if (packageName == context.packageName) return@mapNotNull null
                InstalledApp(
                    packageName = packageName,
                    label = packageManager.getApplicationLabel(applicationInfo).toString(),
                    isSystem = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .distinctBy(InstalledApp::packageName)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, InstalledApp::label))
            .toList()
    }
}
