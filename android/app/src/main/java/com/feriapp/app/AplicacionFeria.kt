package com.feriapp.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.runBlocking
import org.osmdroid.config.Configuration
import java.io.File

class AplicacionFeria : Application() {

    val contenedorApp: ContenedorApp by lazy { ContenedorApp(this) }

    override fun onCreate() {
        super.onCreate()

        val modoOscuroGuardado = runBlocking {
            contenedorApp.gestorPreferencias.obtenerModoOscuro()
        }
        AppCompatDelegate.setDefaultNightMode(
            if (modoOscuroGuardado) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        Configuration.getInstance().userAgentValue = packageName
        val carpetaCache = File(cacheDir, "osmdroid")
        Configuration.getInstance().osmdroidBasePath = carpetaCache
        Configuration.getInstance().osmdroidTileCache = File(carpetaCache, "tiles")
    }
}
