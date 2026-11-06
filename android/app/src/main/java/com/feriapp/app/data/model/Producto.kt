package com.feriapp.app.data.model

data class Producto(
    val idProducto: Int,
    val nombreProducto: String,
    val descripcion: String?,
    val precio: Double,
    val disponible: Boolean,
    val idCategoria: Int,
    val nombreCategoria: String,
    val imagenes: List<Imagen>,
)
