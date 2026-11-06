package com.feriapp.app.data.model

data class DetalleComerciante(
    val idComerciante: Int,
    val idUsuario: Int,
    val nombre: String,
    val apellido: String,
    val telefono: String,
    val nombreComercial: String,
    val descripcion: String?,
    val estado: String,
    val fechaRegistro: String,
    val puesto: PuestoPerfil?,
    val productos: List<ProductoResumen>,
)
