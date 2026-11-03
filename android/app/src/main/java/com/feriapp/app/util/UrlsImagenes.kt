package com.feriapp.app.util

import com.feriapp.app.BuildConfig

fun construirUrlImagen(ruta: String?): String? {
    if (ruta.isNullOrBlank()) return null
    val baseSinBarra = BuildConfig.URL_BASE.removeSuffix("/")
    val rutaConBarra = if (ruta.startsWith("/")) ruta else "/$ruta"
    return baseSinBarra + rutaConBarra
}
