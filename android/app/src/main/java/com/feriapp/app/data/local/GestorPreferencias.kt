package com.feriapp.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStorePreferencias: DataStore<Preferences> by preferencesDataStore(name = "preferencias_feriapp")

class GestorPreferencias(contexto: Context) {

    private val contextoAplicacion = contexto.applicationContext

    companion object {
        private val CLAVE_MODO_OSCURO = booleanPreferencesKey("modo_oscuro")
    }

    val modoOscuro: Flow<Boolean> = contextoAplicacion.dataStorePreferencias.data.map { preferencias ->
        preferencias[CLAVE_MODO_OSCURO] ?: false
    }

    suspend fun obtenerModoOscuro(): Boolean = modoOscuro.first()

    suspend fun guardarModoOscuro(activo: Boolean) {
        contextoAplicacion.dataStorePreferencias.edit { preferencias ->
            preferencias[CLAVE_MODO_OSCURO] = activo
        }
    }
}
