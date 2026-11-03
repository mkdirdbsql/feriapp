package com.feriapp.app.data.remote

import com.feriapp.app.data.remote.dto.CategoriaDto
import com.feriapp.app.data.remote.dto.ComercianteDetalleDto
import com.feriapp.app.data.remote.dto.ComercianteResumenDto
import com.feriapp.app.data.remote.dto.PerfilComercianteDto
import com.feriapp.app.data.remote.dto.ProductoBusquedaDto
import com.feriapp.app.data.remote.dto.ProductoDto
import com.feriapp.app.data.remote.dto.PuestoDto
import com.feriapp.app.data.remote.dto.ReporteDto
import com.feriapp.app.data.remote.dto.RespuestaCrearProducto
import com.feriapp.app.data.remote.dto.RespuestaCrearPuesto
import com.feriapp.app.data.remote.dto.RespuestaEliminacion
import com.feriapp.app.data.remote.dto.RespuestaEstado
import com.feriapp.app.data.remote.dto.RespuestaImagenPuesto
import com.feriapp.app.data.remote.dto.RespuestaImagenesProducto
import com.feriapp.app.data.remote.dto.RespuestaMensaje
import com.feriapp.app.data.remote.dto.RespuestaSesion
import com.feriapp.app.data.remote.dto.RespuestaUbicacion
import com.feriapp.app.data.remote.dto.SolicitudActualizarComerciante
import com.feriapp.app.data.remote.dto.SolicitudActualizarProducto
import com.feriapp.app.data.remote.dto.SolicitudActualizarPuesto
import com.feriapp.app.data.remote.dto.SolicitudBusqueda
import com.feriapp.app.data.remote.dto.SolicitudCrearProducto
import com.feriapp.app.data.remote.dto.SolicitudCrearPuesto
import com.feriapp.app.data.remote.dto.SolicitudEstado
import com.feriapp.app.data.remote.dto.SolicitudInicioSesion
import com.feriapp.app.data.remote.dto.SolicitudRegistroComerciante
import com.feriapp.app.data.remote.dto.SolicitudUbicacion
import com.feriapp.app.data.remote.dto.UbicacionDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ServicioApi {

    // Autenticación
    @POST("api/auth/login")
    suspend fun iniciarSesion(
        @Body solicitud: SolicitudInicioSesion,
    ): Response<RespuestaSesion>

    @POST("api/comerciantes/registro")
    suspend fun registrarComerciante(
        @Body solicitud: SolicitudRegistroComerciante,
    ): Response<RespuestaSesion>

    // Comerciante autenticado
    @GET("api/comerciantes/yo")
    suspend fun obtenerMiPerfil(): Response<PerfilComercianteDto>

    @PUT("api/comerciantes/yo")
    suspend fun actualizarMiPerfil(
        @Body solicitud: SolicitudActualizarComerciante,
    ): Response<RespuestaMensaje>

    // Categorías
    @GET("api/categorias")
    suspend fun obtenerCategorias(): Response<List<CategoriaDto>>

    // Puestos
    @GET("api/puestos")
    suspend fun obtenerPuestos(): Response<List<PuestoDto>>

    @GET("api/puestos/cercanos")
    suspend fun obtenerPuestosCercanos(
        @Query("lat") latitud: Double,
        @Query("lng") longitud: Double,
        @Query("radio") radio: Int? = null,
    ): Response<List<PuestoDto>>

    @GET("api/puestos/{id}")
    suspend fun obtenerPuesto(
        @Path("id") idPuesto: Int,
    ): Response<PuestoDto>

    @POST("api/puestos")
    suspend fun crearPuesto(
        @Body solicitud: SolicitudCrearPuesto,
    ): Response<RespuestaCrearPuesto>

    @PUT("api/puestos/{id}")
    suspend fun actualizarPuesto(
        @Path("id") idPuesto: Int,
        @Body solicitud: SolicitudActualizarPuesto,
    ): Response<RespuestaMensaje>

    @GET("api/puestos/{id}/ubicacion")
    suspend fun obtenerUbicacionPuesto(
        @Path("id") idPuesto: Int,
    ): Response<UbicacionDto>

    @PUT("api/puestos/{id}/ubicacion")
    suspend fun actualizarUbicacionPuesto(
        @Path("id") idPuesto: Int,
        @Body solicitud: SolicitudUbicacion,
    ): Response<RespuestaUbicacion>

    // Productos
    @GET("api/productos")
    suspend fun buscarProductos(
        @Query("nombre") nombre: String? = null,
        @Query("id_categoria") idCategoria: Int? = null,
    ): Response<List<ProductoBusquedaDto>>

    @GET("api/productos/puesto/{idPuesto}")
    suspend fun obtenerProductosDePuesto(
        @Path("idPuesto") idPuesto: Int,
    ): Response<List<ProductoDto>>

    @POST("api/productos")
    suspend fun crearProducto(
        @Body solicitud: SolicitudCrearProducto,
    ): Response<RespuestaCrearProducto>

    @PUT("api/productos/{id}")
    suspend fun actualizarProducto(
        @Path("id") idProducto: Int,
        @Body solicitud: SolicitudActualizarProducto,
    ): Response<RespuestaMensaje>

    @DELETE("api/productos/{id}")
    suspend fun eliminarProducto(
        @Path("id") idProducto: Int,
    ): Response<RespuestaMensaje>

    // Imágenes
    @Multipart
    @POST("api/productos/{id}/imagenes")
    suspend fun subirImagenesProducto(
        @Path("id") idProducto: Int,
        @Part imagenes: List<MultipartBody.Part>,
    ): Response<RespuestaImagenesProducto>

    @DELETE("api/productos/{id}/imagenes/{idImagen}")
    suspend fun eliminarImagenProducto(
        @Path("id") idProducto: Int,
        @Path("idImagen") idImagen: Int,
    ): Response<RespuestaMensaje>

    @Multipart
    @POST("api/puestos/{id}/imagen")
    suspend fun subirImagenPuesto(
        @Path("id") idPuesto: Int,
        @Part imagen: MultipartBody.Part,
    ): Response<RespuestaImagenPuesto>

    @DELETE("api/puestos/{id}/imagen")
    suspend fun eliminarImagenPuesto(
        @Path("id") idPuesto: Int,
    ): Response<RespuestaMensaje>

    // Búsquedas
    @POST("api/busquedas")
    suspend fun registrarBusqueda(
        @Body solicitud: SolicitudBusqueda,
    ): Response<RespuestaMensaje>

    // Administrador
    @GET("api/admin/comerciantes")
    suspend fun listarComerciantes(): Response<List<ComercianteResumenDto>>

    @GET("api/admin/comerciantes/{id}")
    suspend fun obtenerComerciante(
        @Path("id") idComerciante: Int,
    ): Response<ComercianteDetalleDto>

    @PUT("api/admin/comerciantes/{id}")
    suspend fun actualizarComerciante(
        @Path("id") idComerciante: Int,
        @Body solicitud: SolicitudActualizarComerciante,
    ): Response<RespuestaMensaje>

    @PUT("api/admin/comerciantes/{id}/estado")
    suspend fun cambiarEstadoComerciante(
        @Path("id") idComerciante: Int,
        @Body solicitud: SolicitudEstado,
    ): Response<RespuestaEstado>

    @DELETE("api/admin/comerciantes/{id}")
    suspend fun eliminarComerciante(
        @Path("id") idComerciante: Int,
    ): Response<RespuestaEliminacion>

    @GET("api/admin/reportes")
    suspend fun obtenerReportes(): Response<ReporteDto>
}
