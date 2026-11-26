package com.feriapp.app.util

import org.osmdroid.util.GeoPoint
import kotlin.math.cos

object RutaUtil {

    private const val METROS_POR_GRADO_LAT = 111320.0

    fun distanciaEnMetros(p1: GeoPoint, p2: GeoPoint): Double {
        return p1.distanceToAsDouble(p2)
    }

    fun proyectarPuntoEnSegmento(punto: GeoPoint, segInicio: GeoPoint, segFin: GeoPoint): GeoPoint {
        val latRad = Math.toRadians(segInicio.latitude)
        val cosLat = cos(latRad)

        val xB = (segFin.longitude - segInicio.longitude) * cosLat * METROS_POR_GRADO_LAT
        val yB = (segFin.latitude - segInicio.latitude) * METROS_POR_GRADO_LAT

        val xP = (punto.longitude - segInicio.longitude) * cosLat * METROS_POR_GRADO_LAT
        val yP = (punto.latitude - segInicio.latitude) * METROS_POR_GRADO_LAT

        val l2 = xB * xB + yB * yB
        if (l2 < 0.0001) {
            return segInicio
        }

        var t = (xP * xB + yP * yB) / l2
        t = t.coerceIn(0.0, 1.0)

        val latProj = segInicio.latitude + t * (segFin.latitude - segInicio.latitude)
        val lonProj = segInicio.longitude + t * (segFin.longitude - segInicio.longitude)

        return GeoPoint(latProj, lonProj)
    }

    fun distanciaPuntoASegmentoMetros(punto: GeoPoint, segInicio: GeoPoint, segFin: GeoPoint): Double {
        val proj = proyectarPuntoEnSegmento(punto, segInicio, segFin)
        return distanciaEnMetros(punto, proj)
    }

    fun encontrarSegmentoMasCercano(posicion: GeoPoint, puntos: List<GeoPoint>): Int {
        if (puntos.size < 2) return 0
        var minDist = Double.MAX_VALUE
        var minIndex = 0

        for (i in 0 until puntos.size - 1) {
            val dist = distanciaPuntoASegmentoMetros(posicion, puntos[i], puntos[i + 1])
            if (dist < minDist) {
                minDist = dist
                minIndex = i
            }
        }
        return minIndex
    }

    fun distanciaMinimaARutaMetros(posicion: GeoPoint, puntos: List<GeoPoint>): Double {
        if (puntos.size < 2) {
            return if (puntos.isNotEmpty()) distanciaEnMetros(posicion, puntos[0]) else Double.MAX_VALUE
        }
        val idx = encontrarSegmentoMasCercano(posicion, puntos)
        return distanciaPuntoASegmentoMetros(posicion, puntos[idx], puntos[idx + 1])
    }

    fun recortarRuta(puntos: List<GeoPoint>, posicion: GeoPoint): List<GeoPoint> {
        if (puntos.isEmpty()) return emptyList()
        if (puntos.size == 1) return listOf(posicion, puntos[0])

        val idx = encontrarSegmentoMasCercano(posicion, puntos)
        val proj = proyectarPuntoEnSegmento(posicion, puntos[idx], puntos[idx + 1])

        val resultado = mutableListOf<GeoPoint>()
        resultado.add(posicion)
        if (distanciaEnMetros(proj, puntos[idx + 1]) > 1.0) {
            resultado.add(proj)
        }
        for (i in (idx + 1) until puntos.size) {
            resultado.add(puntos[i])
        }
        return resultado
    }
}
