package com.feriapp.app.data.model

data class PerfilComerciante(
    val idComerciante: Int,
    val idUsuario: Int,
    val nombre: String,
    val apellido: String,
    val telefono: String,
    val nombreComercial: String,
    val descripcion: String?,
    val estado: String,
    val puesto: PuestoPerfil?,
    val productos: List<Producto>,
)
