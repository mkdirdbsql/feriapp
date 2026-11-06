package com.feriapp.app.data.model

data class Comerciante(
    val idComerciante: Int,
    val idUsuario: Int,
    val nombre: String,
    val apellido: String,
    val telefono: String,
    val nombreComercial: String,
    val estado: String,
    val fechaRegistro: String,
    val idPuesto: Int?,
    val nombrePuesto: String?,
)
