package com.feriapp.app.data.model

data class ProductoResumen(
    val idProducto: Int,
    val nombreProducto: String,
    val descripcion: String?,
    val precio: Double,
    val disponible: Boolean,
    val nombreCategoria: String,
)
