package com.feriapp.app.data.repository

import com.feriapp.app.data.local.GestorSesion
import com.feriapp.app.data.model.Comerciante
import com.feriapp.app.data.model.DetalleComerciante
import com.feriapp.app.data.model.Reporte
import com.feriapp.app.data.model.aModelo
import com.feriapp.app.data.remote.ServicioApi
import com.feriapp.app.data.remote.dto.*
import com.feriapp.app.util.Resultado
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

typealias RepositorioAdmin = RepositorioAdministracion

class RepositorioAdministracion(
    private val servicioApi: ServicioApi,
    private val gestorSesion: GestorSesion,
) {
    suspend fun listarComerciantes(): Resultado<List<Comerciante>> = listar()

    suspend fun listar(): Resultado<List<Comerciante>> {
        val resultado = ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.listarComerciantes()
        }
        return when (resultado) {
            is Resultado.Exito -> Resultado.Exito(resultado.datos.map { it.aModelo() })
            is Resultado.Error -> Resultado.Error(resultado.mensaje, resultado.codigo)
            is Resultado.Cargando -> Resultado.Cargando
        }
    }

    suspend fun obtenerComerciante(idComerciante: Int): Resultado<DetalleComerciante> = obtener(idComerciante)

    suspend fun obtener(idComerciante: Int): Resultado<DetalleComerciante> {
        val resultado = ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.obtenerComerciante(idComerciante)
        }
        return when (resultado) {
            is Resultado.Exito -> Resultado.Exito(resultado.datos.aModelo())
            is Resultado.Error -> Resultado.Error(resultado.mensaje, resultado.codigo)
            is Resultado.Cargando -> Resultado.Cargando
        }
    }

    suspend fun actualizar(idComerciante: Int, solicitud: SolicitudActualizarComerciante): Resultado<RespuestaMensaje> {
        return ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.actualizarComerciante(idComerciante, solicitud)
        }
    }

    suspend fun cambiarEstado(idComerciante: Int, solicitud: SolicitudEstado): Resultado<RespuestaEstado> {
        return ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.cambiarEstadoComerciante(idComerciante, solicitud)
        }
    }

    suspend fun eliminar(idComerciante: Int): Resultado<RespuestaEliminacion> {
        return ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.eliminarComerciante(idComerciante)
        }
    }

    suspend fun obtenerReportes(): Resultado<Reporte> {
        val resultado = ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.obtenerReportes()
        }
        return when (resultado) {
            is Resultado.Exito -> Resultado.Exito(resultado.datos.aModelo())
            is Resultado.Error -> Resultado.Error(resultado.mensaje, resultado.codigo)
            is Resultado.Cargando -> Resultado.Cargando
        }
    }

    suspend fun descargarReporte(formato: String, destino: File): Resultado<File> = withContext(Dispatchers.IO) {
        try {
            val token = gestorSesion.obtenerToken()
            if (token.isNullOrBlank()) {
                return@withContext Resultado.Error("No hay sesión activa", 401)
            }

            val respuesta = servicioApi.descargarReporte(formato)
            if (respuesta.isSuccessful) {
                val body = respuesta.body() ?: return@withContext Resultado.Error("Cuerpo de respuesta vacío")
                destino.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    FileOutputStream(destino).use { output ->
                        input.copyTo(output)
                    }
                }
                Resultado.Exito(destino)
            } else {
                val codigo = respuesta.code()
                val errorBody = respuesta.errorBody()?.string()
                val mensajeError = try {
                    if (!errorBody.isNullOrBlank()) {
                        val json = JSONObject(errorBody)
                        json.optString("error", json.optString("mensaje", "Error al descargar el reporte"))
                    } else {
                        "Error al descargar el reporte"
                    }
                } catch (e: Exception) {
                    "Error al descargar el reporte"
                }
                Resultado.Error(mensajeError, codigo)
            }
        } catch (e: Exception) {
            Resultado.Error(e.localizedMessage ?: "Error de conexión al descargar reporte")
        }
    }
}
