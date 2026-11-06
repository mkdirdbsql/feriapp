package com.feriapp.app

import android.content.Context
import com.feriapp.app.data.local.GestorSesion
import com.feriapp.app.data.remote.ClienteApi
import com.feriapp.app.data.repository.*

class ContenedorApp(contexto: Context) {
    val gestorSesion: GestorSesion by lazy { GestorSesion(contexto) }
    val clienteApi: ClienteApi by lazy { ClienteApi(gestorSesion) }

    val repositorioAutenticacion: RepositorioAutenticacion by lazy {
        RepositorioAutenticacion(clienteApi.servicio, gestorSesion)
    }
    val repositorioCategorias: RepositorioCategorias by lazy {
        RepositorioCategorias(clienteApi.servicio, gestorSesion)
    }
    val repositorioPuestos: RepositorioPuestos by lazy {
        RepositorioPuestos(clienteApi.servicio, gestorSesion)
    }
    val repositorioProductos: RepositorioProductos by lazy {
        RepositorioProductos(clienteApi.servicio, gestorSesion)
    }
    val repositorioComerciante: RepositorioComerciante by lazy {
        RepositorioComerciante(clienteApi.servicio, gestorSesion)
    }
    val repositorioAdministracion: RepositorioAdministracion by lazy {
        RepositorioAdministracion(clienteApi.servicio, gestorSesion)
    }
}
