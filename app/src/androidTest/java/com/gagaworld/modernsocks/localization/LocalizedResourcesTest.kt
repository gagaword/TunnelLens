package com.gagaworld.modernsocks.localization

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gagaworld.modernsocks.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalizedResourcesTest {
    @Test
    fun englishAndSimplifiedChineseResourcesResolveIndependently() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val english = context.forLocale(Locale.ENGLISH)
        val chinese = context.forLocale(Locale.forLanguageTag("zh-CN"))

        assertEquals("Home", english.getString(R.string.navigation_home))
        assertEquals("首页", chinese.getString(R.string.navigation_home))
        assertEquals("Settings", english.getString(R.string.settings_title))
        assertEquals("设置", chinese.getString(R.string.settings_title))
        assertNotEquals(
            english.getString(R.string.profile_edit_new_title),
            chinese.getString(R.string.profile_edit_new_title),
        )
        assertEquals(
            "已导入 2 个配置",
            chinese.resources.getQuantityString(R.plurals.profiles_imported, 2, 2),
        )
    }
}

private fun Context.forLocale(locale: Locale): Context {
    val configuration = Configuration(resources.configuration).apply {
        setLocales(LocaleList(locale))
    }
    return createConfigurationContext(configuration)
}
