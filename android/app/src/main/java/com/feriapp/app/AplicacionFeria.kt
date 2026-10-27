package com.feriapp.app

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class AplicacionFeria : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName
        val carpetaCache = File(cacheDir, "osmdroid")
        Configuration.getInstance().osmdroidBasePath = carpetaCache
        Configuration.getInstance().osmdroidTileCache = File(carpetaCache, "tiles")
    }
}
