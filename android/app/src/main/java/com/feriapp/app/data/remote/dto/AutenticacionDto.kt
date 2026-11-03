package com.feriapp.app.data.remote.dto

data class SolicitudInicioSesion(
    val identificador: String,
    val contrasena: String,
)

data class SolicitudRegistroComerciante(
    val nombre: String,
    val apellido: String,
    val telefono: String,
    val contrasena: String,
    val nombreComercial: String,
    val descripcionComercio: String? = null,
)

data class RespuestaSesion(
    val token: String,
    val rol: String,
    val idUsuario: Int,
    val idComerciante: Int?,
    val nombre: String,
)
