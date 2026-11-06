package com.feriapp.app.data.repository

import com.feriapp.app.data.local.GestorSesion
import com.feriapp.app.data.model.PerfilComerciante
import com.feriapp.app.data.model.aModelo
import com.feriapp.app.data.remote.ServicioApi
import com.feriapp.app.data.remote.dto.RespuestaMensaje
import com.feriapp.app.data.remote.dto.SolicitudActualizarComerciante
import com.feriapp.app.util.Resultado

class RepositorioComerciante(
    private val servicioApi: ServicioApi,
    private val gestorSesion: GestorSesion,
) {
    suspend fun obtenerMiPerfil(): Resultado<PerfilComerciante> {
        val resultado = ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.obtenerMiPerfil()
        }
        return when (resultado) {
            is Resultado.Exito -> Resultado.Exito(resultado.datos.aModelo())
            is Resultado.Error -> Resultado.Error(resultado.mensaje, resultado.codigo)
            is Resultado.Cargando -> Resultado.Cargando
        }
    }

    suspend fun actualizarMiPerfil(solicitud: SolicitudActualizarComerciante): Resultado<RespuestaMensaje> {
        return ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.actualizarMiPerfil(solicitud)
        }
    }
}
