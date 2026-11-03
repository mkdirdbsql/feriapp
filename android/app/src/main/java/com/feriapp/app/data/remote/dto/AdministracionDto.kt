package com.feriapp.app.data.remote.dto

data class ComercianteResumenDto(
    val idComerciante: Int,
    val idUsuario: Int,
    val nombre: String,
    val apellido: String,
    val telefono: String,
    val nombreComercial: String,
    val estado: String,
    val fechaRegistro: String,
    val idPuesto: Int?,
    val nombrePuesto: String?,
)

data class ComercianteDetalleDto(
    val idComerciante: Int,
    val idUsuario: Int,
    val nombre: String,
    val apellido: String,
    val telefono: String,
    val nombreComercial: String,
    val descripcion: String?,
    val estado: String,
    val fechaRegistro: String,
    val puesto: PuestoPerfilDto?,
    val productos: List<ProductoResumenDto>,
)

data class SolicitudEstado(
    val estado: String,
)

data class RespuestaEstado(
    val mensaje: String?,
    val estado: String,
)

data class RespuestaEliminacion(
    val mensaje: String?,
    val archivosBorrados: Int,
)

data class ReporteDto(
    val usuariosPorRol: List<UsuariosPorRolDto>,
    val categoriaMasBuscada: CategoriaBuscadaDto?,
    val usuariosRegistradosHoy: Int,
)

data class UsuariosPorRolDto(
    val rol: String,
    val cantidad: Int,
)

data class CategoriaBuscadaDto(
    val categoria: String,
    val cantidad: Int,
)
