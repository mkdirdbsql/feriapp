package com.feriapp.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStoreSesion: DataStore<Preferences> by preferencesDataStore(name = "sesion_feriapp")

data class Sesion(
    val token: String,
    val rol: String,
    val idUsuario: Int,
    val idComerciante: Int?,
    val nombre: String,
)

class GestorSesion(contexto: Context) {

    private val contextoAplicacion = contexto.applicationContext

    companion object {
        private val CLAVE_TOKEN = stringPreferencesKey("token")
        private val CLAVE_ROL = stringPreferencesKey("rol")
        private val CLAVE_ID_USUARIO = intPreferencesKey("id_usuario")
        private val CLAVE_ID_COMERCIANTE = intPreferencesKey("id_comerciante")
        private val CLAVE_NOMBRE = stringPreferencesKey("nombre")
    }

    val sesion: Flow<Sesion?> = contextoAplicacion.dataStoreSesion.data.map { preferencias ->
        val token = preferencias[CLAVE_TOKEN]
        val rol = preferencias[CLAVE_ROL]
        val idUsuario = preferencias[CLAVE_ID_USUARIO]
        val idComerciante = preferencias[CLAVE_ID_COMERCIANTE]
        val nombre = preferencias[CLAVE_NOMBRE]

        if (token != null && rol != null && idUsuario != null && nombre != null) {
            Sesion(
                token = token,
                rol = rol,
                idUsuario = idUsuario,
                idComerciante = idComerciante,
                nombre = nombre,
            )
        } else {
            null
        }
    }

    suspend fun obtenerSesion(): Sesion? = sesion.first()

    suspend fun obtenerToken(): String? = contextoAplicacion.dataStoreSesion.data.map { preferencias ->
        preferencias[CLAVE_TOKEN]
    }.first()

    suspend fun guardarSesion(
        token: String,
        rol: String,
        idUsuario: Int,
        idComerciante: Int?,
        nombre: String,
    ) {
        contextoAplicacion.dataStoreSesion.edit { preferencias ->
            preferencias[CLAVE_TOKEN] = token
            preferencias[CLAVE_ROL] = rol
            preferencias[CLAVE_ID_USUARIO] = idUsuario
            preferencias[CLAVE_NOMBRE] = nombre
            if (idComerciante != null) {
                preferencias[CLAVE_ID_COMERCIANTE] = idComerciante
            } else {
                preferencias.remove(CLAVE_ID_COMERCIANTE)
            }
        }
    }

    suspend fun cerrarSesion() {
        contextoAplicacion.dataStoreSesion.edit { preferencias ->
            preferencias.clear()
        }
    }
}
