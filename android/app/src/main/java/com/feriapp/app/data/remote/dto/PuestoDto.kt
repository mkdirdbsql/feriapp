package com.feriapp.app.data.remote.dto

data class PuestoDto(
    val idPuesto: Int,
    val nombrePuesto: String,
    val descripcion: String?,
    val estado: String?,
    val longitud: Double,
    val latitud: Double,
    val nombreComercial: String,
    val imagen: String?,
    val distanciaMetros: Double?,
)

data class PuestoPerfilDto(
    val idPuesto: Int,
    val nombrePuesto: String,
    val descripcion: String?,
    val estado: String,
    val longitud: Double,
    val latitud: Double,
    val imagen: String?,
)

data class UbicacionDto(
    val latitud: Double,
    val longitud: Double,
)

data class SolicitudCrearPuesto(
    val nombrePuesto: String,
    val descripcion: String? = null,
    val latitud: Double,
    val longitud: Double,
)

data class SolicitudActualizarPuesto(
    val nombrePuesto: String? = null,
    val descripcion: String? = null,
)

data class SolicitudUbicacion(
    val latitud: Double,
    val longitud: Double,
)

data class RespuestaCrearPuesto(
    val mensaje: String?,
    val idPuesto: Int,
    val latitud: Double,
    val longitud: Double,
)

data class RespuestaUbicacion(
    val mensaje: String?,
    val latitud: Double,
    val longitud: Double,
)

data class RespuestaImagenPuesto(
    val mensaje: String?,
    val idImagen: Int,
    val rutaImagen: String,
)
