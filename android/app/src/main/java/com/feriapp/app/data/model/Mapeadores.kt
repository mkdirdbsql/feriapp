package com.feriapp.app.data.model

import com.feriapp.app.data.remote.dto.*
import com.feriapp.app.util.construirUrlImagen

fun CategoriaDto.aModelo(): Categoria = Categoria(
    idCategoria = idCategoria,
    nombreCategoria = nombreCategoria,
    descripcion = descripcion,
)

fun ImagenDto.aModelo(): Imagen = Imagen(
    idImagen = idImagen,
    url = construirUrlImagen(rutaImagen),
)

fun PuestoDto.aModelo(): Puesto = Puesto(
    idPuesto = idPuesto,
    nombrePuesto = nombrePuesto,
    descripcion = descripcion,
    estado = estado,
    longitud = longitud,
    latitud = latitud,
    nombreComercial = nombreComercial,
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
    idProducto = idProducto,
    nombreProducto = nombreProducto,
    descripcion = descripcion,
    precio = precio,
    disponible = disponible,
    idCategoria = idCategoria,
    nombreCategoria = nombreCategoria,
    imagenes = imagenes.map { it.aModelo() },
)

fun ProductoResumenDto.aModelo(): ProductoResumen = ProductoResumen(
    idProducto = idProducto,
    nombreProducto = nombreProducto,
    descripcion = descripcion,
    precio = precio,
    disponible = disponible,
    nombreCategoria = nombreCategoria,
)

fun ProductoBusquedaDto.aModelo(): ProductoBuscado = ProductoBuscado(
    idProducto = idProducto,
    nombreProducto = nombreProducto,
    descripcion = descripcion,
    precio = precio,
    disponible = disponible,
    idCategoria = idCategoria,
    nombreCategoria = nombreCategoria,
    idPuesto = idPuesto,
    nombrePuesto = nombrePuesto,
    nombreComercial = nombreComercial,
    longitud = longitud,
    latitud = latitud,
    imagenProductoUrl = construirUrlImagen(imagenProducto),
    imagenPuestoUrl = construirUrlImagen(imagenPuesto),
)

fun PerfilComercianteDto.aModelo(): PerfilComerciante = PerfilComerciante(
    idComerciante = idComerciante,
    idUsuario = idUsuario,
    nombre = nombre,
    apellido = apellido,
    telefono = telefono,
    nombreComercial = nombreComercial,
    descripcion = descripcion,
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
    nombreComercial = nombreComercial,
    estado = estado,
    fechaRegistro = fechaRegistro,
    idPuesto = idPuesto,
    nombrePuesto = nombrePuesto,
)

fun ComercianteDetalleDto.aModelo(): DetalleComerciante = DetalleComerciante(
    idComerciante = idComerciante,
    idUsuario = idUsuario,
    nombre = nombre,
    apellido = apellido,
    telefono = telefono,
    nombreComercial = nombreComercial,
    descripcion = descripcion,
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
