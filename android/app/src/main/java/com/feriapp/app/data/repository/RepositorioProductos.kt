package com.feriapp.app.data.repository

import com.feriapp.app.data.local.GestorSesion
import com.feriapp.app.data.model.Producto
import com.feriapp.app.data.model.ProductoBuscado
import com.feriapp.app.data.model.aModelo
import com.feriapp.app.data.remote.ServicioApi
import com.feriapp.app.data.remote.dto.*
import com.feriapp.app.util.Resultado
import okhttp3.MultipartBody

class RepositorioProductos(
    private val servicioApi: ServicioApi,
    private val gestorSesion: GestorSesion,
) {
    suspend fun buscar(nombre: String? = null, idCategoria: Int? = null): Resultado<List<ProductoBuscado>> {
        val resultado = ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.buscarProductos(nombre, idCategoria)
        }
        return when (resultado) {
            is Resultado.Exito -> Resultado.Exito(resultado.datos.map { it.aModelo() })
            is Resultado.Error -> Resultado.Error(resultado.mensaje, resultado.codigo)
            is Resultado.Cargando -> Resultado.Cargando
        }
    }

    suspend fun obtenerDePuesto(idPuesto: Int): Resultado<List<Producto>> {
        val resultado = ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.obtenerProductosDePuesto(idPuesto)
        }
        return when (resultado) {
            is Resultado.Exito -> Resultado.Exito(resultado.datos.map { it.aModelo() })
            is Resultado.Error -> Resultado.Error(resultado.mensaje, resultado.codigo)
            is Resultado.Cargando -> Resultado.Cargando
        }
    }

    suspend fun crear(solicitud: SolicitudCrearProducto): Resultado<RespuestaCrearProducto> {
        return ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.crearProducto(solicitud)
        }
    }

    suspend fun actualizar(idProducto: Int, solicitud: SolicitudActualizarProducto): Resultado<RespuestaMensaje> {
        return ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.actualizarProducto(idProducto, solicitud)
        }
    }

    suspend fun eliminar(idProducto: Int): Resultado<RespuestaMensaje> {
        return ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.eliminarProducto(idProducto)
        }
    }

    suspend fun subirImagenes(idProducto: Int, imagenes: List<MultipartBody.Part>): Resultado<RespuestaImagenesProducto> {
        return ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.subirImagenesProducto(idProducto, imagenes)
        }
    }

    suspend fun eliminarImagen(idProducto: Int, idImagen: Int): Resultado<RespuestaMensaje> {
        return ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.eliminarImagenProducto(idProducto, idImagen)
        }
    }

    suspend fun registrarBusqueda(solicitud: SolicitudBusqueda): Resultado<RespuestaMensaje> {
        return ejecutarLlamadaSegura(gestorSesion) {
            servicioApi.registrarBusqueda(solicitud)
        }
    }
}
