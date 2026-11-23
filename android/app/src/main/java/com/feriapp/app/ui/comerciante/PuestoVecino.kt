package com.feriapp.app.ui.comerciante

import com.feriapp.app.data.model.ProductoBuscado

data class CategoriaFiltroComerciante(
    val idCategoria: Int,
    val nombreCategoria: String
)

data class PuestoVecino(
    val idPuesto: Int,
    val nombrePuesto: String,
    val nombreComercial: String,
    val latitud: Double,
    val longitud: Double,
    val imagenPuestoUrl: String?,
    val productos: List<ProductoBuscado>
)
