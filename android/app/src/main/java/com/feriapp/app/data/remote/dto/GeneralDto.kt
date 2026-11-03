package com.feriapp.app.data.remote.dto

data class ErrorApiDto(
    val error: String?,
)

data class RespuestaMensaje(
    val mensaje: String?,
)

data class SolicitudBusqueda(
    val termino: String,
    val idCategoria: Int? = null,
    val idProducto: Int? = null,
)
