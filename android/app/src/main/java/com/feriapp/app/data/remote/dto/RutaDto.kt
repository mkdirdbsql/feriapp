package com.feriapp.app.data.remote.dto

data class RespuestaRutaOsrmDto(
    val code: String?,
    val routes: List<RutaOsrmDto>?,
)

data class RutaOsrmDto(
    val geometry: GeometriaRutaDto?,
)

data class GeometriaRutaDto(
    val coordinates: List<List<Double>>?,
)
