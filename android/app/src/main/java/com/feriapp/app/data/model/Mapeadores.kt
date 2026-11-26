package com.feriapp.app.data.model

import com.feriapp.app.data.remote.dto.*
import com.feriapp.app.util.construirUrlImagen

fun parsearPrecio(valor: Any?): Double {
    return when (valor) {
        is Number -> valor.toDouble()
        is String -> valor.replace(',', '.').toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
}

fun parsearDisponible(valor: Any?): Boolean {
    return when (valor) {
        is Boolean -> valor
        is Number -> valor.toInt() != 0
        is String -> valor.equals("true", ignoreCase = true) || valor == "1"
        else -> true
    }
}

fun CategoriaDto.aModelo(): Categoria = Categoria(
    idCategoria = idCategoria,
    nombreCategoria = nombreCategoria,
    descripcion = descripcion,
)

fun ImagenDto.aModelo(): Imagen = Imagen(
    idImagen = idImagen ?: 0,
    url = construirUrlImagen(rutaImagen),
)

fun PuestoDto.aModelo(): Puesto = Puesto(
    idPuesto = idPuesto,
    nombrePuesto = nombrePuesto,
    descripcion = descripcion,
    estado = estado,
    longitud = longitud,
    latitud = latitud,
    imagenUrl = construirUrlImagen(imagen),
    distanciaMetros = distanciaMetros,
)

fun PuestoPerfilDto.aModelo(): PuestoPerfil = PuestoPerfil(
    idPuesto = idPuesto,
    nombrePuesto = nombrePuesto,
    descripcion = descripcion,
    estado = estado,
    longitud = longitud,
    latitud = latitud,
    imagenUrl = construirUrlImagen(imagen),
)

fun ProductoDto.aModelo(): Producto = Producto(
    idProducto = idProducto ?: 0,
    nombreProducto = nombreProducto ?: "",
    descripcion = descripcion,
    precio = parsearPrecio(precio),
    disponible = parsearDisponible(disponible),
    idCategoria = idCategoria ?: 0,
    nombreCategoria = nombreCategoria ?: "",
    imagenes = imagenes?.map { it.aModelo() } ?: emptyList(),
)

fun ProductoResumenDto.aModelo(): ProductoResumen = ProductoResumen(
    idProducto = idProducto ?: 0,
    nombreProducto = nombreProducto ?: "",
    descripcion = descripcion,
    precio = parsearPrecio(precio),
    disponible = parsearDisponible(disponible),
    nombreCategoria = nombreCategoria ?: "",
    imagenes = imagenes?.map { it.aModelo() } ?: emptyList(),
)

fun ProductoBusquedaDto.aModelo(): ProductoBuscado = ProductoBuscado(
    idProducto = idProducto ?: 0,
    nombreProducto = nombreProducto ?: "",
    descripcion = descripcion,
    precio = parsearPrecio(precio),
    disponible = parsearDisponible(disponible),
    idCategoria = idCategoria ?: 0,
    nombreCategoria = nombreCategoria ?: "",
    idPuesto = idPuesto ?: 0,
    nombrePuesto = nombrePuesto ?: "",
    longitud = longitud ?: 0.0,
    latitud = latitud ?: 0.0,
    imagenProductoUrl = construirUrlImagen(imagenProducto),
    imagenPuestoUrl = construirUrlImagen(imagenPuesto),
)

fun PerfilComercianteDto.aModelo(): PerfilComerciante = PerfilComerciante(
    idComerciante = idComerciante,
    idUsuario = idUsuario,
    nombre = nombre,
    apellido = apellido,
    telefono = telefono,
    estado = estado,
    puesto = puesto?.aModelo(),
    productos = productos.map { it.aModelo() },
)

fun ComercianteResumenDto.aModelo(): Comerciante = Comerciante(
    idComerciante = idComerciante,
    idUsuario = idUsuario,
    nombre = nombre,
    apellido = apellido,
    telefono = telefono,
    estado = estado,
    fechaRegistro = fechaRegistro,
    idPuesto = idPuesto,
    nombrePuesto = nombrePuesto,
    latitud = latitud,
    longitud = longitud,
)

fun ComercianteDetalleDto.aModelo(): DetalleComerciante = DetalleComerciante(
    idComerciante = idComerciante,
    idUsuario = idUsuario,
    nombre = nombre,
    apellido = apellido,
    telefono = telefono,
    estado = estado,
    fechaRegistro = fechaRegistro,
    puesto = puesto?.aModelo(),
    productos = productos.map { it.aModelo() },
)

fun UsuariosPorRolDto.aModelo(): UsuariosPorRol = UsuariosPorRol(
    rol = rol,
    cantidad = cantidad,
)

fun CategoriaBuscadaDto.aModelo(): CategoriaBuscada = CategoriaBuscada(
    categoria = categoria,
    cantidad = cantidad,
)

fun ReporteDto.aModelo(): Reporte = Reporte(
    usuariosPorRol = usuariosPorRol.map { it.aModelo() },
    categoriaMasBuscada = categoriaMasBuscada?.aModelo(),
    usuariosRegistradosHoy = usuariosRegistradosHoy,
)
