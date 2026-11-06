package com.feriapp.app.data.repository

import com.feriapp.app.data.local.GestorSesion
import com.feriapp.app.data.model.Categoria
import com.feriapp.app.data.model.aModelo
import com.feriapp.app.data.remote.ServicioApi
import com.feriapp.app.util.Resultado

class RepositorioCategorias(
    private val servicioApi: ServicioApi,
    private val gestorSesion: GestorSesion,
) {
    suspend fun obtenerCategorias(): Resultado<List<Categoria>> {
        val resultado = ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.obtenerCategorias()
        }
        return when (resultado) {
            is Resultado.Exito -> Resultado.Exito(resultado.datos.map { it.aModelo() })
            is Resultado.Error -> Resultado.Error(resultado.mensaje, resultado.codigo)
            is Resultado.Cargando -> Resultado.Cargando
        }
    }
}
