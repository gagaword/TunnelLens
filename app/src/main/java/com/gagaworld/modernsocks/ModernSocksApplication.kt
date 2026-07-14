package com.gagaworld.modernsocks

import android.app.Application
import com.gagaworld.modernsocks.app.AppContainer
import com.gagaworld.modernsocks.app.DefaultAppContainer

class ModernSocksApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DefaultAppContainer(this)
    }
}
