package com.feriapp.app.data.repository

import com.feriapp.app.data.local.GestorSesion
import com.feriapp.app.data.model.Puesto
import com.feriapp.app.data.model.aModelo
import com.feriapp.app.data.remote.ServicioApi
import com.feriapp.app.data.remote.dto.*
import com.feriapp.app.util.Resultado
import okhttp3.MultipartBody

class RepositorioPuestos(
    private val servicioApi: ServicioApi,
    private val gestorSesion: GestorSesion,
) {
    suspend fun listar(): Resultado<List<Puesto>> {
        val resultado = ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.obtenerPuestos()
        }
        return when (resultado) {
            is Resultado.Exito -> Resultado.Exito(resultado.datos.map { it.aModelo() })
            is Resultado.Error -> Resultado.Error(resultado.mensaje, resultado.codigo)
            is Resultado.Cargando -> Resultado.Cargando
        }
    }

    suspend fun cercanos(latitud: Double, longitud: Double, radio: Int? = null): Resultado<List<Puesto>> {
        val resultado = ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.obtenerPuestosCercanos(latitud, longitud, radio)
        }
        return when (resultado) {
            is Resultado.Exito -> Resultado.Exito(resultado.datos.map { it.aModelo() })
            is Resultado.Error -> Resultado.Error(resultado.mensaje, resultado.codigo)
            is Resultado.Cargando -> Resultado.Cargando
        }
    }

    suspend fun obtener(idPuesto: Int): Resultado<Puesto> {
        val resultado = ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.obtenerPuesto(idPuesto)
        }
        return when (resultado) {
            is Resultado.Exito -> Resultado.Exito(resultado.datos.aModelo())
            is Resultado.Error -> Resultado.Error(resultado.mensaje, resultado.codigo)
            is Resultado.Cargando -> Resultado.Cargando
        }
    }

    suspend fun crear(solicitud: SolicitudCrearPuesto): Resultado<RespuestaCrearPuesto> {
        return ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.crearPuesto(solicitud)
        }
    }

    suspend fun actualizar(idPuesto: Int, solicitud: SolicitudActualizarPuesto): Resultado<RespuestaMensaje> {
        return ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.actualizarPuesto(idPuesto, solicitud)
        }
    }

    suspend fun obtenerUbicacion(idPuesto: Int): Resultado<UbicacionDto> {
        return ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.obtenerUbicacionPuesto(idPuesto)
        }
    }

    suspend fun actualizarUbicacion(idPuesto: Int, solicitud: SolicitudUbicacion): Resultado<RespuestaUbicacion> {
        return ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.actualizarUbicacionPuesto(idPuesto, solicitud)
        }
    }

    suspend fun subirImagen(idPuesto: Int, imagen: MultipartBody.Part): Resultado<RespuestaImagenPuesto> {
        return ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.subirImagenPuesto(idPuesto, imagen)
        }
    }

    suspend fun eliminarImagen(idPuesto: Int): Resultado<RespuestaMensaje> {
        return ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.eliminarImagenPuesto(idPuesto)
        }
    }
}
