package com.feriapp.app.ui.comerciante

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feriapp.app.data.model.Categoria
import com.feriapp.app.data.model.Producto
import com.feriapp.app.data.remote.dto.SolicitudActualizarProducto
import com.feriapp.app.data.remote.dto.SolicitudCrearProducto
import com.feriapp.app.data.repository.RepositorioCategorias
import com.feriapp.app.data.repository.RepositorioComerciante
import com.feriapp.app.data.repository.RepositorioProductos
import com.feriapp.app.util.Resultado
import com.feriapp.app.util.limpiarArchivosTemporalesImagenes
import com.feriapp.app.util.prepararParteImagen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class EstadoCategorias {
    data object Cargando : EstadoCategorias()
    data class Lista(val categorias: List<Categoria>) : EstadoCategorias()
    data class Error(val mensaje: String) : EstadoCategorias()
}

sealed class EstadoGuardadoProducto {
    data object Inactivo : EstadoGuardadoProducto()
    data object Guardando : EstadoGuardadoProducto()
    data class Exito(val imagenFallo: Boolean) : EstadoGuardadoProducto()
    data object ExitoEliminado : EstadoGuardadoProducto()
    data class Error(val mensaje: String) : EstadoGuardadoProducto()
}

sealed class EstadoPrecargaProducto {
    data object Inactivo : EstadoPrecargaProducto()
    data class Listo(val producto: Producto) : EstadoPrecargaProducto()
}

class AgregarProductoViewModel(
    private val repositorioCategorias: RepositorioCategorias,
    private val repositorioProductos: RepositorioProductos,
    private val repositorioComerciante: RepositorioComerciante,
    contexto: Context,
) : ViewModel() {

    private val contextoAplicacion = contexto.applicationContext

    private val _estadoCategorias = MutableStateFlow<EstadoCategorias>(EstadoCategorias.Cargando)
    val estadoCategorias: StateFlow<EstadoCategorias> = _estadoCategorias.asStateFlow()

    private val _estadoGuardadoProducto = MutableStateFlow<EstadoGuardadoProducto>(EstadoGuardadoProducto.Inactivo)
    val estadoGuardadoProducto: StateFlow<EstadoGuardadoProducto> = _estadoGuardadoProducto.asStateFlow()

    private val _estadoPrecarga = MutableStateFlow<EstadoPrecargaProducto>(EstadoPrecargaProducto.Inactivo)
    val estadoPrecarga: StateFlow<EstadoPrecargaProducto> = _estadoPrecarga.asStateFlow()

    var idCategoriaSeleccionada: Int? = null
    val uriImagenState = MutableStateFlow<Uri?>(null)
    var uriFotoCamaraPendiente: Uri? = null

    // Edición
    var idProductoEditar: Int = -1
    var modoEdicion: Boolean = false
    var idImagenActual: Int? = null
    var imagenOriginalUrl: String? = null
    var imagenQuitadaManualmente: Boolean = false

    var nombreInicial: String = ""
    var descripcionInicial: String = ""
    var precioInicial: Double = 0.0
    var disponibleInicial: Boolean = true
    var idCategoriaInicial: Int? = null
    var precargadoHecho: Boolean = false

    init {
        cargarCategorias()
    }

    fun cargarCategorias() {
        viewModelScope.launch {
            _estadoCategorias.value = EstadoCategorias.Cargando
            when (val res = repositorioCategorias.obtenerCategorias()) {
                is Resultado.Exito -> {
                    _estadoCategorias.value = EstadoCategorias.Lista(res.datos)
                }
                is Resultado.Error -> {
                    _estadoCategorias.value = EstadoCategorias.Error(res.mensaje)
                }
                is Resultado.Cargando -> {
                    _estadoCategorias.value = EstadoCategorias.Cargando
                }
            }
        }
    }

    fun reintentarCategorias() {
        cargarCategorias()
    }

    fun precargarProductoParaEdicion(idProducto: Int) {
        if (precargadoHecho || idProducto == -1) return
        idProductoEditar = idProducto
        modoEdicion = true

        viewModelScope.launch {
            when (val resPerfil = repositorioComerciante.obtenerMiPerfil()) {
                is Resultado.Exito -> {
                    val producto = resPerfil.datos.productos.find { it.idProducto == idProducto }
                    if (producto != null) {
                        nombreInicial = producto.nombreProducto
                        descripcionInicial = producto.descripcion.orEmpty()
                        precioInicial = producto.precio
                        disponibleInicial = producto.disponible
                        idCategoriaInicial = producto.idCategoria
                        idCategoriaSeleccionada = producto.idCategoria

                        val primeraImagen = producto.imagenes.firstOrNull()
                        idImagenActual = primeraImagen?.idImagen
                        imagenOriginalUrl = primeraImagen?.url

                        if (!imagenOriginalUrl.isNullOrBlank()) {
                            uriImagenState.value = Uri.parse(imagenOriginalUrl)
                        }

                        precargadoHecho = true
                        _estadoPrecarga.value = EstadoPrecargaProducto.Listo(producto)
                    }
                }
                else -> {}
            }
        }
    }

    fun guardarProducto(
        nombre: String,
        descripcion: String,
        precio: Double,
        disponible: Boolean,
    ) {
        val idCategoria = idCategoriaSeleccionada
        if (idCategoria == null) {
            _estadoGuardadoProducto.value = EstadoGuardadoProducto.Error("Selecciona una categoría.")
            return
        }

        viewModelScope.launch {
            _estadoGuardadoProducto.value = EstadoGuardadoProducto.Guardando

            if (!modoEdicion) {
                // Modo crear
                val solicitud = SolicitudCrearProducto(
                    idCategoria = idCategoria,
                    nombreProducto = nombre,
                    descripcion = descripcion.ifBlank { null },
                    precio = precio,
                    disponible = disponible,
                )

                when (val resCrear = repositorioProductos.crear(solicitud)) {
                    is Resultado.Exito -> {
                        val idProducto = resCrear.datos.idProducto
                        var imagenFallo = false
                        val uri = uriImagenState.value

                        if (uri != null) {
                            val parteImagen = prepararParteImagen(contextoAplicacion, uri, "imagenes")
                            if (parteImagen != null) {
                                val resImagen = repositorioProductos.subirImagenes(idProducto, listOf(parteImagen))
                                if (resImagen !is Resultado.Exito) {
                                    imagenFallo = true
                                }
                            } else {
                                imagenFallo = true
                            }
                        }

                        limpiarArchivosTemporalesImagenes(contextoAplicacion)
                        _estadoGuardadoProducto.value = EstadoGuardadoProducto.Exito(imagenFallo)
                    }
                    is Resultado.Error -> {
                        limpiarArchivosTemporalesImagenes(contextoAplicacion)
                        _estadoGuardadoProducto.value = EstadoGuardadoProducto.Error(resCrear.mensaje)
                    }
                    is Resultado.Cargando -> {
                        _estadoGuardadoProducto.value = EstadoGuardadoProducto.Guardando
                    }
                }
            } else {
                // Modo edición
                val solicitud = SolicitudActualizarProducto(
                    nombreProducto = nombre,
                    descripcion = descripcion.ifBlank { null },
                    precio = precio,
                    disponible = disponible,
                    idCategoria = idCategoria,
                )

                when (val resActualizar = repositorioProductos.actualizar(idProductoEditar, solicitud)) {
                    is Resultado.Exito -> {
                        var imagenFallo = false
                        val uriActual = uriImagenState.value

                        if (uriActual != null && uriActual.toString() != imagenOriginalUrl) {
                            if (idImagenActual != null) {
                                repositorioProductos.eliminarImagen(idProductoEditar, idImagenActual!!)
                            }
                            val parteImagen = prepararParteImagen(contextoAplicacion, uriActual, "imagenes")
                            if (parteImagen != null) {
                                val resImagen = repositorioProductos.subirImagenes(idProductoEditar, listOf(parteImagen))
                                if (resImagen !is Resultado.Exito) {
                                    imagenFallo = true
                                }
                            } else {
                                imagenFallo = true
                            }
                        } else if (uriActual == null && idImagenActual != null && imagenQuitadaManualmente) {
                            repositorioProductos.eliminarImagen(idProductoEditar, idImagenActual!!)
                        }

                        limpiarArchivosTemporalesImagenes(contextoAplicacion)
                        _estadoGuardadoProducto.value = EstadoGuardadoProducto.Exito(imagenFallo)
                    }
                    is Resultado.Error -> {
                        limpiarArchivosTemporalesImagenes(contextoAplicacion)
                        _estadoGuardadoProducto.value = EstadoGuardadoProducto.Error(resActualizar.mensaje)
                    }
                    is Resultado.Cargando -> {
                        _estadoGuardadoProducto.value = EstadoGuardadoProducto.Guardando
                    }
                }
            }
        }
    }

    fun eliminarProducto() {
        if (!modoEdicion || idProductoEditar == -1) return
        viewModelScope.launch {
            _estadoGuardadoProducto.value = EstadoGuardadoProducto.Guardando
            when (val res = repositorioProductos.eliminar(idProductoEditar)) {
                is Resultado.Exito -> {
                    limpiarArchivosTemporalesImagenes(contextoAplicacion)
                    _estadoGuardadoProducto.value = EstadoGuardadoProducto.ExitoEliminado
                }
                is Resultado.Error -> {
                    limpiarArchivosTemporalesImagenes(contextoAplicacion)
                    _estadoGuardadoProducto.value = EstadoGuardadoProducto.Error(res.mensaje)
                }
                is Resultado.Cargando -> {
                    _estadoGuardadoProducto.value = EstadoGuardadoProducto.Guardando
                }
            }
        }
    }

    fun resetearEstadoGuardado() {
        _estadoGuardadoProducto.value = EstadoGuardadoProducto.Inactivo
    }
}
