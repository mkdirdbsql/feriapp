package com.feriapp.app.data.remote

import com.feriapp.app.data.local.GestorSesion
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class InterceptorAutenticacion(
    private val gestorSesion: GestorSesion,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val peticionOriginal = chain.request()

        if (peticionOriginal.header("Authorization") != null) {
            return chain.proceed(peticionOriginal)
        }

        val token = runBlocking {
            gestorSesion.obtenerToken()
        }

        if (token.isNullOrBlank()) {
            return chain.proceed(peticionOriginal)
        }

        val peticionConToken = peticionOriginal.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(peticionConToken)
    }
}
