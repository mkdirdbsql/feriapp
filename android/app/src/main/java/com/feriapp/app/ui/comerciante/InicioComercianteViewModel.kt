package com.feriapp.app.ui.comerciante

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feriapp.app.data.model.PerfilComerciante
import com.feriapp.app.data.model.ProductoBuscado
import com.feriapp.app.data.model.PuestoPerfil
import com.feriapp.app.data.remote.dto.SolicitudActualizarPuesto
import com.feriapp.app.data.remote.dto.SolicitudCrearPuesto
import com.feriapp.app.data.remote.dto.SolicitudUbicacion
import com.feriapp.app.data.repository.RepositorioComerciante
import com.feriapp.app.data.repository.RepositorioProductos
import com.feriapp.app.data.repository.RepositorioPuestos
import com.feriapp.app.ui.consumidor.CamaraMapa
import com.feriapp.app.util.Resultado
import com.feriapp.app.util.limpiarArchivosTemporalesImagenes
import com.feriapp.app.util.prepararParteImagen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FaseAgregarPuesto {
    Inactiva,
    BuscandoUbicacion,
    DialogoDatos
}

sealed class EstadoInicioComerciante {
    data object Cargando : EstadoInicioComerciante()
    data class SinPuesto(val perfil: PerfilComerciante) : EstadoInicioComerciante()
    data class ConPuesto(val perfil: PerfilComerciante, val puesto: PuestoPerfil, val cantidadProductos: Int) : EstadoInicioComerciante()
    data class Error(val mensaje: String) : EstadoInicioComerciante()
}

sealed class EstadoGuardadoPuesto {
    data object Inactivo : EstadoGuardadoPuesto()
    data object Guardando : EstadoGuardadoPuesto()
    data class Exito(val imagenFallo: Boolean = false) : EstadoGuardadoPuesto()
    data class Error(val mensaje: String) : EstadoGuardadoPuesto()
}

class InicioComercianteViewModel(
    private val repositorioComerciante: RepositorioComerciante,
    private val repositorioPuestos: RepositorioPuestos,
    private val repositorioProductos: RepositorioProductos,
    contexto: Context
) : ViewModel() {

    private val contextoAplicacion = contexto.applicationContext

    private val _estado = MutableStateFlow<EstadoInicioComerciante>(EstadoInicioComerciante.Cargando)
    val estado: StateFlow<EstadoInicioComerciante> = _estado.asStateFlow()

    private val _estadoGuardado = MutableStateFlow<EstadoGuardadoPuesto>(EstadoGuardadoPuesto.Inactivo)
    val estadoGuardado: StateFlow<EstadoGuardadoPuesto> = _estadoGuardado.asStateFlow()

    private val _categoriasDelComerciante = MutableStateFlow<List<CategoriaFiltroComerciante>>(emptyList())
    val categoriasDelComerciante: StateFlow<List<CategoriaFiltroComerciante>> = _categoriasDelComerciante.asStateFlow()

    private val _vecinos = MutableStateFlow<List<PuestoVecino>>(emptyList())
    val vecinos: StateFlow<List<PuestoVecino>> = _vecinos.asStateFlow()

    var ubicacionActiva: Boolean = false
    var dialogoUbicacionVisible: Boolean = false
    var centradoInicialHecho: Boolean = false
    var camaraGuardada: CamaraMapa? = null
    var menuAbierto: Boolean = false
    var globoDescartado: Boolean = false

    var fase: FaseAgregarPuesto = FaseAgregarPuesto.Inactiva
    var latitudPuesto: Double? = null
    var longitudPuesto: Double? = null
    var nombreBorrador: String = ""
    var uriImagenBorrador: Uri? = null

    // Borradores de edición de puesto
    var modoEdicionDialogo: Boolean = false
    var nombreEdicionBorrador: String? = null
    var uriImagenEdicionBorrador: Uri? = null
    var latitudEdicionNueva: Double? = null
    var longitudEdicionNueva: Double? = null
    var ubicacionEdicionActualizada: Boolean = false

    var categoriasActivas: Set<Int> = emptySet()
    var idPuestoVecinoSeleccionado: Int? = null
    private val resultadosPorCategoria = mutableMapOf<Int, List<ProductoBuscado>>()

    init {
        cargarPerfil()
    }

    fun cargarPerfil() {
        viewModelScope.launch {
            _estado.value = EstadoInicioComerciante.Cargando
            when (val resultado = repositorioComerciante.obtenerMiPerfil()) {
                is Resultado.Exito -> {
                    val perfil = resultado.datos
                    if (perfil.puesto == null) {
                        _categoriasDelComerciante.value = emptyList()
                        _vecinos.value = emptyList()
                        _estado.value = EstadoInicioComerciante.SinPuesto(perfil)
                    } else {
                        val categoriasComerciante = perfil.productos
                            .distinctBy { it.idCategoria }
                            .take(3)
                            .map { CategoriaFiltroComerciante(it.idCategoria, it.nombreCategoria) }

                        val idNuevasCategorias = categoriasComerciante.map { it.idCategoria }.toSet()
                        if (categoriasActivas.isEmpty()) {
                            categoriasActivas = idNuevasCategorias
                        } else {
                            categoriasActivas = categoriasActivas.filter { it in idNuevasCategorias }.toSet() + idNuevasCategorias
                        }

                        _categoriasDelComerciante.value = categoriasComerciante
                        resultadosPorCategoria.clear()

                        _estado.value = EstadoInicioComerciante.ConPuesto(
                            perfil = perfil,
                            puesto = perfil.puesto,
                            cantidadProductos = perfil.productos.size
                        )

                        if (categoriasActivas.isNotEmpty()) {
                            cargarVecinos()
                        } else {
                            _vecinos.value = emptyList()
                        }
                    }
                }
                is Resultado.Error -> {
                    _estado.value = EstadoInicioComerciante.Error(resultado.mensaje)
                }
                is Resultado.Cargando -> {
                    _estado.value = EstadoInicioComerciante.Cargando
                }
            }
        }
    }

    fun recargarPerfil() {
        cargarPerfil()
    }

    fun cargarVecinos(alError: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val estadoActual = _estado.value
            val miPuestoId = (estadoActual as? EstadoInicioComerciante.ConPuesto)?.puesto?.idPuesto ?: return@launch

            for (idCat in categoriasActivas) {
                if (!resultadosPorCategoria.containsKey(idCat)) {
                    when (val res = repositorioProductos.buscar(nombre = null, idCategoria = idCat)) {
                        is Resultado.Exito -> {
                            resultadosPorCategoria[idCat] = res.datos
                        }
                        is Resultado.Error -> {
                            alError?.invoke("No se pudieron cargar los otros puestos")
                            categoriasActivas = categoriasActivas - idCat
                        }
                        is Resultado.Cargando -> {}
                    }
                }
            }

            val todosProductos = categoriasActivas.flatMap { idCat ->
                resultadosPorCategoria[idCat].orEmpty()
            }

            val vecinosAgrupados = todosProductos
                .filter { it.idPuesto != miPuestoId && it.idCategoria in categoriasActivas }
                .groupBy { it.idPuesto }
                .map { (idPuestoVecino, productosDeEstePuesto) ->
                    val primer = productosDeEstePuesto.first()
                    PuestoVecino(
                        idPuesto = idPuestoVecino,
                        nombrePuesto = primer.nombrePuesto,
                        latitud = primer.latitud,
                        longitud = primer.longitud,
                        imagenPuestoUrl = primer.imagenPuestoUrl,
                        productos = productosDeEstePuesto.distinctBy { it.idProducto }.take(3)
                    )
                }

            _vecinos.value = vecinosAgrupados

            if (idPuestoVecinoSeleccionado != null && vecinosAgrupados.none { it.idPuesto == idPuestoVecinoSeleccionado }) {
                idPuestoVecinoSeleccionado = null
            }
        }
    }

    fun alternarCategoria(
        idCategoria: Int,
        activa: Boolean,
        alError: ((String) -> Unit)? = null,
        alSinPuestos: ((String) -> Unit)? = null
    ) {
        if (activa) {
            categoriasActivas = categoriasActivas + idCategoria
        } else {
            categoriasActivas = categoriasActivas - idCategoria
        }

        viewModelScope.launch {
            val estadoActual = _estado.value
            val miPuestoId = (estadoActual as? EstadoInicioComerciante.ConPuesto)?.puesto?.idPuesto ?: 0

            if (activa && !resultadosPorCategoria.containsKey(idCategoria)) {
                when (val res = repositorioProductos.buscar(nombre = null, idCategoria = idCategoria)) {
                    is Resultado.Exito -> {
                        resultadosPorCategoria[idCategoria] = res.datos
                    }
                    is Resultado.Error -> {
                        alError?.invoke("No se pudieron cargar los otros puestos")
                        categoriasActivas = categoriasActivas - idCategoria
                    }
                    is Resultado.Cargando -> {}
                }
            }

            val prodsOtrosPuestosCat = resultadosPorCategoria[idCategoria]?.filter { it.idPuesto != miPuestoId }.orEmpty()
            if (activa && prodsOtrosPuestosCat.isEmpty()) {
                alSinPuestos?.invoke("Aún no hay otros puestos con esta categoría")
            }

            cargarVecinos(alError)
        }
    }

    fun guardarPuesto(
        nombre: String,
        latitud: Double,
        longitud: Double,
        uriImagen: Uri?,
        alErrorSubirImagen: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            _estadoGuardado.value = EstadoGuardadoPuesto.Guardando
            val solicitud = SolicitudCrearPuesto(nombrePuesto = nombre, latitud = latitud, longitud = longitud)
            when (val resCrear = repositorioPuestos.crear(solicitud)) {
                is Resultado.Exito -> {
                    val idPuesto = resCrear.datos.idPuesto
                    if (uriImagen != null) {
                        val parteImagen = prepararParteImagen(contextoAplicacion, uriImagen, "imagen")
                        if (parteImagen != null) {
                            val resImagen = repositorioPuestos.subirImagen(idPuesto, parteImagen)
                            if (resImagen !is Resultado.Exito) {
                                alErrorSubirImagen?.invoke()
                            }
                        } else {
                            alErrorSubirImagen?.invoke()
                        }
                    }
                    fase = FaseAgregarPuesto.Inactiva
                    nombreBorrador = ""
                    uriImagenBorrador = null
                    latitudPuesto = null
                    longitudPuesto = null
                    centradoInicialHecho = false
                    _estadoGuardado.value = EstadoGuardadoPuesto.Inactivo
                    limpiarArchivosTemporalesImagenes(contextoAplicacion)
                    recargarPerfil()
                }
                is Resultado.Error -> {
                    limpiarArchivosTemporalesImagenes(contextoAplicacion)
                    _estadoGuardado.value = EstadoGuardadoPuesto.Error(resCrear.mensaje)
                    if (resCrear.codigo == 409 || resCrear.mensaje.contains("409") || resCrear.mensaje.contains("Ya tienes un puesto")) {
                        recargarPerfil()
                    }
                }
                is Resultado.Cargando -> {
                    _estadoGuardado.value = EstadoGuardadoPuesto.Guardando
                }
            }
        }
    }

    fun guardarEdicionPuesto(
        idPuesto: Int,
        nombre: String,
        nuevaLat: Double?,
        nuevaLon: Double?,
        uriImagenNueva: Uri?
    ) {
        viewModelScope.launch {
            _estadoGuardado.value = EstadoGuardadoPuesto.Guardando

            val solNombre = SolicitudActualizarPuesto(nombrePuesto = nombre.trim())
            when (val resNombre = repositorioPuestos.actualizar(idPuesto, solNombre)) {
                is Resultado.Exito -> {}
                is Resultado.Error -> {
                    _estadoGuardado.value = EstadoGuardadoPuesto.Error(resNombre.mensaje)
                    return@launch
                }
                is Resultado.Cargando -> {}
            }

            if (nuevaLat != null && nuevaLon != null) {
                val solUbicacion = SolicitudUbicacion(latitud = nuevaLat, longitud = nuevaLon)
                when (val resUbicacion = repositorioPuestos.actualizarUbicacion(idPuesto, solUbicacion)) {
                    is Resultado.Exito -> {}
                    is Resultado.Error -> {
                        _estadoGuardado.value = EstadoGuardadoPuesto.Error(resUbicacion.mensaje)
                        return@launch
                    }
                    is Resultado.Cargando -> {}
                }
            }

            if (uriImagenNueva != null) {
                val parteImagen = prepararParteImagen(contextoAplicacion, uriImagenNueva, "imagen")
                if (parteImagen != null) {
                    val resImagen = repositorioPuestos.subirImagen(idPuesto, parteImagen)
                    if (resImagen !is Resultado.Exito) {
                        Log.d("FeriApp", "No se pudo actualizar la imagen del puesto")
                    }
                }
            }

            limpiarBorradorEdicion()
            limpiarArchivosTemporalesImagenes(contextoAplicacion)
            _estadoGuardado.value = EstadoGuardadoPuesto.Exito(imagenFallo = false)
            recargarPerfil()
        }
    }

    fun limpiarBorradorEdicion() {
        modoEdicionDialogo = false
        nombreEdicionBorrador = null
        uriImagenEdicionBorrador = null
        latitudEdicionNueva = null
        longitudEdicionNueva = null
        ubicacionEdicionActualizada = false
    }

    fun resetearEstadoGuardado() {
        _estadoGuardado.value = EstadoGuardadoPuesto.Inactivo
    }
}
