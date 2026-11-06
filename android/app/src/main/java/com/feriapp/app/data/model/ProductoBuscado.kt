package com.feriapp.app.data.model

data class ProductoBuscado(
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
    val imagenProductoUrl: String?,
    val imagenPuestoUrl: String?,
)
