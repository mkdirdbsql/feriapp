package com.feriapp.app.data.model

data class Puesto(
    val idPuesto: Int,
    val nombrePuesto: String,
    val descripcion: String?,
    val estado: String?,
    val longitud: Double,
    val latitud: Double,
    val nombreComercial: String,
    val imagenUrl: String?,
    val distanciaMetros: Double?,
)
