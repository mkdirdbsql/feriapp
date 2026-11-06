package com.feriapp.app.data.repository

import com.feriapp.app.data.local.GestorSesion
import com.feriapp.app.data.local.Sesion
import com.feriapp.app.data.remote.ServicioApi
import com.feriapp.app.data.remote.dto.RespuestaSesion
import com.feriapp.app.data.remote.dto.SolicitudInicioSesion
import com.feriapp.app.data.remote.dto.SolicitudRegistroComerciante
import com.feriapp.app.util.Resultado
import kotlinx.coroutines.flow.Flow

class RepositorioAutenticacion(
    private val servicioApi: ServicioApi,
    private val gestorSesion: GestorSesion,
) {
    val sesion: Flow<Sesion?> = gestorSesion.sesion

    suspend fun iniciarSesion(identificador: String, contrasena: String): Resultado<RespuestaSesion> {
        val resultado = ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.iniciarSesion(SolicitudInicioSesion(identificador, contrasena))
        }
        if (resultado is Resultado.Exito) {
            val sesionDto = resultado.datos
            gestorSesion.guardarSesion(
                token = sesionDto.token,
                rol = sesionDto.rol,
                idUsuario = sesionDto.idUsuario,
                idComerciante = sesionDto.idComerciante,
                nombre = sesionDto.nombre,
            )
        }
        return resultado
    }

    suspend fun registrarComerciante(
        nombre: String,
        apellido: String,
        telefono: String,
        contrasena: String,
        nombreComercial: String,
        descripcionComercio: String?,
    ): Resultado<RespuestaSesion> {
        val resultado = ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.registrarComerciante(
                SolicitudRegistroComerciante(
                    nombre = nombre,
                    apellido = apellido,
                    telefono = telefono,
                    contrasena = contrasena,
                    nombreComercial = nombreComercial,
                    descripcionComercio = descripcionComercio,
                )
            )
        }
        if (resultado is Resultado.Exito) {
            val sesionDto = resultado.datos
            gestorSesion.guardarSesion(
                token = sesionDto.token,
                rol = sesionDto.rol,
                idUsuario = sesionDto.idUsuario,
                idComerciante = sesionDto.idComerciante,
                nombre = sesionDto.nombre,
            )
        }
        return resultado
    }

    suspend fun cerrarSesion() {
        gestorSesion.cerrarSesion()
    }
}
