package com.feriapp.app.util

import org.osmdroid.util.BoundingBox

object LimitesFeria {
    const val LATITUD_MINIMA = -16.505
    const val LATITUD_MAXIMA = -16.482
    const val LONGITUD_MINIMA = -68.195
    const val LONGITUD_MAXIMA = -68.165

    val cajaLimites = BoundingBox(
        LATITUD_MAXIMA,
        LONGITUD_MAXIMA,
        LATITUD_MINIMA,
        LONGITUD_MINIMA,
    )

    fun estaDentro(latitud: Double, longitud: Double): Boolean {
        return (latitud in LATITUD_MINIMA..LATITUD_MAXIMA) && (longitud in LONGITUD_MINIMA..LONGITUD_MAXIMA)
    }
}
