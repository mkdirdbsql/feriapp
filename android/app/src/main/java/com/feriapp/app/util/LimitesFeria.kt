package com.feriapp.app.util

import org.osmdroid.util.BoundingBox
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object LimitesFeria {
    // Perímetro de la Feria 16 de Julio: (latitud, longitud) en orden alrededor del borde.
    val PERIMETRO: List<Pair<Double, Double>> = listOf(
        -16.503136 to -68.162684,
        -16.494270 to -68.194010,
        -16.486726 to -68.191200,
        -16.486755 to -68.180093,
        -16.488210 to -68.171081,
        -16.494377 to -68.166632,
        -16.496850 to -68.163779,
    )

    // El GPS tiene error de unos metros: un punto a pocos metros del borde cuenta como dentro.
    private const val TOLERANCIA_METROS = 15.0
    // Margen (aprox. 300 m) para que el mapa pueda moverse un poco más allá del perímetro.
    private const val MARGEN_GRADOS = 0.003
    private const val METROS_POR_GRADO_LAT = 110574.0

    val LATITUD_MINIMA: Double = PERIMETRO.minOf { it.first } - MARGEN_GRADOS
    val LATITUD_MAXIMA: Double = PERIMETRO.maxOf { it.first } + MARGEN_GRADOS
    val LONGITUD_MINIMA: Double = PERIMETRO.minOf { it.second } - MARGEN_GRADOS
    val LONGITUD_MAXIMA: Double = PERIMETRO.maxOf { it.second } + MARGEN_GRADOS

    val cajaLimites = BoundingBox(LATITUD_MAXIMA, LONGITUD_MAXIMA, LATITUD_MINIMA, LONGITUD_MINIMA)

    fun estaDentro(latitud: Double, longitud: Double): Boolean {
        if (dentroDelPoligono(latitud, longitud)) return true
        return distanciaAlBordeMetros(latitud, longitud) <= TOLERANCIA_METROS
    }

    private fun dentroDelPoligono(lat: Double, lon: Double): Boolean {
        var dentro = false
        var j = PERIMETRO.size - 1
        for (i in PERIMETRO.indices) {
            val (latI, lonI) = PERIMETRO[i]
            val (latJ, lonJ) = PERIMETRO[j]
            val cruza = (lonI > lon) != (lonJ > lon) &&
                lat < (latJ - latI) * (lon - lonI) / (lonJ - lonI) + latI
            if (cruza) dentro = !dentro
            j = i
        }
        return dentro
    }

    private fun distanciaAlBordeMetros(lat: Double, lon: Double): Double {
        val metrosPorGradoLon = 111320.0 * cos(lat * PI / 180.0)
        fun aMetros(p: Pair<Double, Double>) =
            Pair((p.second - lon) * metrosPorGradoLon, (p.first - lat) * METROS_POR_GRADO_LAT)
        var minima = Double.MAX_VALUE
        for (i in PERIMETRO.indices) {
            val (ax, ay) = aMetros(PERIMETRO[i])
            val (bx, by) = aMetros(PERIMETRO[(i + 1) % PERIMETRO.size])
            val dx = bx - ax
            val dy = by - ay
            val largo2 = dx * dx + dy * dy
            val t = if (largo2 == 0.0) 0.0 else max(0.0, min(1.0, (-ax * dx - ay * dy) / largo2))
            val distancia = hypot(ax + t * dx, ay + t * dy)
            if (distancia < minima) minima = distancia
        }
        return minima
    }
}
