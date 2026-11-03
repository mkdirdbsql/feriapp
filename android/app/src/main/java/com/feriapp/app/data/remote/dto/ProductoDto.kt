package com.feriapp.app.data.remote.dto

data class ImagenDto(
    val idImagen: Int,
    val rutaImagen: String,
)

data class ProductoBusquedaDto(
    val idProducto: Int,
    val nombreProducto: String,
    val descripcion: String?,
    val precio: Double,
    val disponible: Boolean,
    val idCategoria: Int,
    val nombreCategoria: String,
    val idPuesto: Int,
    val nombrePuesto: String,
    val nombreComercial: String,
    val longitud: Double,
    val latitud: Double,
    val imagenProducto: String?,
    val imagenPuesto: String?,
)

data class ProductoDto(
    val idProducto: Int,
    val nombreProducto: String,
    val descripcion: String?,
    val precio: Double,
    val disponible: Boolean,
    val idCategoria: Int,
    val nombreCategoria: String,
    val imagenes: List<ImagenDto>,
)

data class ProductoResumenDto(
    val idProducto: Int,
    val nombreProducto: String,
    val descripcion: String?,
    val precio: Double,
    val disponible: Boolean,
    val nombreCategoria: String,
)

data class SolicitudCrearProducto(
    val idCategoria: Int,
    val nombreProducto: String,
    val descripcion: String? = null,
    val precio: Double,
    val disponible: Boolean? = null,
)

data class SolicitudActualizarProducto(
    val nombreProducto: String? = null,
    val descripcion: String? = null,
    val precio: Double? = null,
    val disponible: Boolean? = null,
)

data class RespuestaCrearProducto(
    val mensaje: String?,
    val idProducto: Int,
    val idPuesto: Int,
)

data class RespuestaImagenesProducto(
    val mensaje: String?,
    val imagenes: List<ImagenDto>,
)
