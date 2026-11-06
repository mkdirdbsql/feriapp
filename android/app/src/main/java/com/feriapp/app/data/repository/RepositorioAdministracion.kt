package com.feriapp.app.data.repository

import com.feriapp.app.data.local.GestorSesion
import com.feriapp.app.data.model.Comerciante
import com.feriapp.app.data.model.DetalleComerciante
import com.feriapp.app.data.model.Reporte
import com.feriapp.app.data.model.aModelo
import com.feriapp.app.data.remote.ServicioApi
import com.feriapp.app.data.remote.dto.*
import com.feriapp.app.util.Resultado

class RepositorioAdministracion(
    private val servicioApi: ServicioApi,
    private val gestorSesion: GestorSesion,
) {
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
}
