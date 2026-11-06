package com.feriapp.app.data.repository

import com.feriapp.app.data.local.GestorSesion
import com.feriapp.app.data.remote.dto.ErrorApiDto
import com.feriapp.app.util.Resultado
import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

suspend fun <T> ejecutarLlamadaSegura(
    gestorSesion: GestorSesion? = null,
    llamada: suspend () -> Response<T>,
): Resultado<T> {
    return try {
        val respuesta = llamada()
        val codigo = respuesta.code()
        if (respuesta.isSuccessful) {
            val cuerpo = respuesta.body()
            if (cuerpo != null) {
                Resultado.Exito(cuerpo)
            } else {
                @Suppress("UNCHECKED_CAST")
                Resultado.Exito(Unit as T)
            }
        } else {
            val errorBodyString = respuesta.errorBody()?.string()
            val errorDto = try {
                if (!errorBodyString.isNullOrBlank()) {
                    Gson().fromJson(errorBodyString, ErrorApiDto::class.java)
                } else null
            } catch (e: Exception) {
                null
            }

            val mensajeError = errorDto?.error ?: "Error en la petición"

            if (codigo == 401 || (codigo == 403 && mensajeError.contains("Cuenta desactivada", ignoreCase = true))) {
                gestorSesion?.cerrarSesion()
            }

            Resultado.Error(mensaje = mensajeError, codigo = codigo)
        }
    } catch (e: IOException) {
        Resultado.Error(mensaje = "Sin conexión a internet. Revisa tu red.")
    } catch (e: SocketTimeoutException) {
        Resultado.Error(mensaje = "El servidor tardó demasiado en responder.")
    } catch (e: Exception) {
        Resultado.Error(mensaje = e.localizedMessage ?: "Ocurrió un error inesperado.")
    }
}
