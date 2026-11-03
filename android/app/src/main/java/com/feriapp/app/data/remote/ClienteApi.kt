package com.feriapp.app.data.remote

import com.feriapp.app.BuildConfig
import com.feriapp.app.data.local.GestorSesion
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ClienteApi(gestorSesion: GestorSesion) {

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private val okHttpClient = OkHttpClient.Builder().apply {
        connectTimeout(15, TimeUnit.SECONDS)
        readTimeout(15, TimeUnit.SECONDS)
        writeTimeout(15, TimeUnit.SECONDS)

        addInterceptor(InterceptorAutenticacion(gestorSesion))

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
                redactHeader("Authorization")
            }
            addInterceptor(loggingInterceptor)
        }
    }.build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.URL_BASE)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val servicio: ServicioApi = retrofit.create(ServicioApi::class.java)
}
