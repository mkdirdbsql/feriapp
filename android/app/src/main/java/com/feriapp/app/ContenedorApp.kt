package com.feriapp.app

import android.content.Context
import com.feriapp.app.data.local.GestorPreferencias
import com.feriapp.app.data.local.GestorSesion
import com.feriapp.app.data.remote.ClienteApi
import com.feriapp.app.data.remote.ServicioRutas
import com.feriapp.app.data.repository.RepositorioAdministracion
import com.feriapp.app.data.repository.RepositorioAutenticacion
import com.feriapp.app.data.repository.RepositorioCategorias
import com.feriapp.app.data.repository.RepositorioComerciante
import com.feriapp.app.data.repository.RepositorioProductos
import com.feriapp.app.data.repository.RepositorioPuestos
import com.feriapp.app.data.repository.RepositorioRutas
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ContenedorApp(contexto: Context) {
    val gestorSesion: GestorSesion by lazy { GestorSesion(contexto) }
    val gestorPreferencias: GestorPreferencias by lazy { GestorPreferencias(contexto) }
    val clienteApi: ClienteApi by lazy { ClienteApi(gestorSesion) }

    private val okHttpClientRutas: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "FeriApp/1.0 (proyecto de grado, Android)")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val retrofitRutas: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://routing.openstreetmap.de/")
            .client(okHttpClientRutas)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val servicioRutas: ServicioRutas by lazy {
        retrofitRutas.create(ServicioRutas::class.java)
    }

    val repositorioRutas: RepositorioRutas by lazy {
        RepositorioRutas(servicioRutas)
    }

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
