package com.feriapp.app.data.model

data class PuestoPerfil(
    val idPuesto: Int,
    val nombrePuesto: String,
    val descripcion: String?,
    val estado: String,
    val longitud: Double,
    val latitud: Double,
    val imagenUrl: String?,
)
