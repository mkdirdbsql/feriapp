package com.feriapp.app.data.model

data class Reporte(
    val usuariosPorRol: List<UsuariosPorRol>,
    val categoriaMasBuscada: CategoriaBuscada?,
    val usuariosRegistradosHoy: Int,
)

data class UsuariosPorRol(
    val rol: String,
    val cantidad: Int,
)

data class CategoriaBuscada(
    val categoria: String,
    val cantidad: Int,
)
