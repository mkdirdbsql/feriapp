package com.feriapp.app.data.remote.dto

data class PerfilComercianteDto(
    val idComerciante: Int,
    val idUsuario: Int,
    val nombre: String,
    val apellido: String,
    val telefono: String,
    val nombreComercial: String,
    val descripcion: String?,
    val estado: String,
    val puesto: PuestoPerfilDto?,
    val productos: List<ProductoDto>,
)

data class SolicitudActualizarComerciante(
    val nombre: String? = null,
    val apellido: String? = null,
    val telefono: String? = null,
    val contrasena: String? = null,
    val nombreComercial: String? = null,
    val descripcion: String? = null,
)
