package com.feriapp.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ImagenDto(
    @SerializedName("id_imagen", alternate = ["id", "idImagen"])
    val idImagen: Int? = null,
    @SerializedName("ruta_imagen", alternate = ["ruta", "url", "rutaImagen", "imagen"])
    val rutaImagen: String? = null,
)

data class ProductoBusquedaDto(
    @SerializedName("id_producto", alternate = ["id", "idProducto"])
    val idProducto: Int? = null,
    @SerializedName("nombre_producto", alternate = ["nombre", "nombreProducto"])
    val nombreProducto: String? = null,
    val descripcion: String? = null,
    val precio: Any? = null,
    val disponible: Any? = null,
    @SerializedName("id_categoria", alternate = ["idCategoria"])
    val idCategoria: Int? = null,
    @SerializedName("nombre_categoria", alternate = ["categoria", "nombreCategoria"])
    val nombreCategoria: String? = null,
    @SerializedName("id_puesto", alternate = ["idPuesto"])
    val idPuesto: Int? = null,
    @SerializedName("nombre_puesto", alternate = ["puesto", "nombrePuesto"])
    val nombrePuesto: String? = null,
    val longitud: Double? = null,
    val latitud: Double? = null,
    @SerializedName("imagen_producto", alternate = ["imagenProducto"])
    val imagenProducto: String? = null,
    @SerializedName("imagen_puesto", alternate = ["imagenPuesto"])
    val imagenPuesto: String? = null,
)

data class ProductoDto(
    @SerializedName("id_producto", alternate = ["id", "idProducto"])
    val idProducto: Int? = null,
    @SerializedName("nombre_producto", alternate = ["nombre", "nombreProducto"])
    val nombreProducto: String? = null,
    val descripcion: String? = null,
    val precio: Any? = null,
    val disponible: Any? = null,
    @SerializedName("id_categoria", alternate = ["idCategoria"])
    val idCategoria: Int? = null,
    @SerializedName("nombre_categoria", alternate = ["categoria", "nombreCategoria"])
    val nombreCategoria: String? = null,
    val imagenes: List<ImagenDto>? = null,
)

data class ProductoResumenDto(
    @SerializedName("id_producto", alternate = ["id", "idProducto"])
    val idProducto: Int? = null,
    @SerializedName("nombre_producto", alternate = ["nombre", "nombreProducto"])
    val nombreProducto: String? = null,
    val descripcion: String? = null,
    val precio: Any? = null,
    val disponible: Any? = null,
    @SerializedName("nombre_categoria", alternate = ["categoria", "nombreCategoria"])
    val nombreCategoria: String? = null,
    val imagenes: List<ImagenDto>? = null,
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
    val idCategoria: Int? = null,
)

data class RespuestaCrearProducto(
    val mensaje: String?,
    val idProducto: Int,
    val idPuesto: Int,
)

data class RespuestaImagenesProducto(
    val mensaje: String?,
    val imagenes: List<ImagenDto>?,
)
