package com.feriapp.app.data.remote

import com.feriapp.app.data.remote.dto.RespuestaRutaOsrmDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface ServicioRutas {

    @GET
    suspend fun obtenerRuta(
        @Url urlCompleta: String
    ): Response<RespuestaRutaOsrmDto>
}
