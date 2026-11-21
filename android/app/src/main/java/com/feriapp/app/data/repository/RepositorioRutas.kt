package com.feriapp.app.data.repository

import android.util.Log
import com.feriapp.app.data.remote.ServicioRutas
import com.feriapp.app.util.Resultado
import org.osmdroid.util.GeoPoint
import java.util.Locale

class RepositorioRutas(
    private val servicioRutas: ServicioRutas,
) {

    suspend fun calcularRutaPie(
        origen: GeoPoint,
        destino: GeoPoint,
    ): Resultado<List<GeoPoint>> {
        val lonOrigen = String.format(Locale.US, "%.6f", origen.longitude)
        val latOrigen = String.format(Locale.US, "%.6f", origen.latitude)
        val lonDestino = String.format(Locale.US, "%.6f", destino.longitude)
        val latDestino = String.format(Locale.US, "%.6f", destino.latitude)

        val urlPie = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/$lonOrigen,$latOrigen;$lonDestino,$latDestino?overview=full&geometries=geojson"
        val urlAuto = "https://router.project-osrm.org/route/v1/driving/$lonOrigen,$latOrigen;$lonDestino,$latDestino?overview=full&geometries=geojson"

        val resultadoPie = realizarPeticionRuta(urlPie)
        if (resultadoPie is Resultado.Exito) {
            return resultadoPie
        }

        val motivoPie = (resultadoPie as? Resultado.Error)?.mensaje ?: "error desconocido"
        Log.d("FeriApp", "Fallo intento 1 (a pie): $motivoPie. Probando intento 2 (conduccion)...")

        val resultadoAuto = realizarPeticionRuta(urlAuto)
        if (resultadoAuto is Resultado.Exito) {
            return resultadoAuto
        }

        val motivoAuto = (resultadoAuto as? Resultado.Error)?.mensaje ?: motivoPie
        Log.d("FeriApp", "Fallo intento 2 (conduccion): $motivoAuto.")

        return Resultado.Error(motivoAuto)
    }

    private suspend fun realizarPeticionRuta(url: String): Resultado<List<GeoPoint>> {
        Log.d("FeriApp", "Consultando URL servicio rutas: $url")
        return try {
            val respuesta = servicioRutas.obtenerRuta(url)
            val codigoHttp = respuesta.code()
            Log.d("FeriApp", "Respuesta HTTP servicio rutas: $codigoHttp")

            if (respuesta.isSuccessful) {
                val dto = respuesta.body()
                val codeOsrm = dto?.code
                Log.d("FeriApp", "Respuesta JSON OSRM code: $codeOsrm")

                val coordenadas = dto?.routes?.firstOrNull()?.geometry?.coordinates
                if (codeOsrm == "Ok" && !coordenadas.isNullOrEmpty()) {
                    val puntos = coordenadas.mapNotNull { par ->
                        if (par.size >= 2) {
                            GeoPoint(par[1], par[0])
                        } else null
                    }
                    Log.d("FeriApp", "Ruta obtenida exitosamente con ${puntos.size} puntos.")
                    Resultado.Exito(puntos)
                } else {
                    val motivo = if (codeOsrm != "Ok") "code $codeOsrm" else "sin ruta posible"
                    Log.d("FeriApp", "Error OSRM: $motivo")
                    Resultado.Error(motivo)
                }
            } else {
                val error = "error HTTP $codigoHttp"
                Log.d("FeriApp", "Error HTTP OSRM: $error")
                Resultado.Error(error)
            }
        } catch (e: Exception) {
            val error = "sin conexión"
            Log.d("FeriApp", "Excepción al consultar OSRM: ${e.localizedMessage}")
            Resultado.Error(error)
        }
    }
}
