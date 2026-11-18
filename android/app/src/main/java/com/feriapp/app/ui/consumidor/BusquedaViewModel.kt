package com.feriapp.app.ui.consumidor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feriapp.app.data.model.Categoria
import com.feriapp.app.data.model.ProductoBuscado
import com.feriapp.app.data.remote.dto.SolicitudBusqueda
import com.feriapp.app.data.repository.RepositorioCategorias
import com.feriapp.app.data.repository.RepositorioProductos
import com.feriapp.app.util.Resultado
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class EstadoBusqueda {
    data object CargandoCategorias : EstadoBusqueda()
    data class ListoCategorias(val categorias: List<Categoria>) : EstadoBusqueda()
    data class Sugerencias(
        val sugerencias: List<ProductoBuscado>,
        val sinCoincidencias: Boolean
    ) : EstadoBusqueda()
    data class Error(val mensaje: String) : EstadoBusqueda()
}

sealed class EstadoResultadosBusqueda {
    data object Inicial : EstadoResultadosBusqueda()
    data object Cargando : EstadoResultadosBusqueda()
    data class Exito(
        val terminoBuscado: String,
        val productosAgrupados: List<ProductoBuscado>
    ) : EstadoResultadosBusqueda()
    data class SinResultados(val mensaje: String) : EstadoResultadosBusqueda()
    data class Error(val mensaje: String) : EstadoResultadosBusqueda()
}

class BusquedaViewModel(
    private val repositorioCategorias: RepositorioCategorias,
    private val repositorioProductos: RepositorioProductos,
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoBusqueda>(EstadoBusqueda.CargandoCategorias)
    val estado: StateFlow<EstadoBusqueda> = _estado.asStateFlow()

    private val _estadoResultados = MutableStateFlow<EstadoResultadosBusqueda>(EstadoResultadosBusqueda.Inicial)
    val estadoResultados: StateFlow<EstadoResultadosBusqueda> = _estadoResultados.asStateFlow()

    private var trabajoBusqueda: Job? = null
    private var categoriasCargadas: List<Categoria> = emptyList()

    fun cargarCategorias() {
        if (categoriasCargadas.isNotEmpty()) {
            _estado.value = EstadoBusqueda.ListoCategorias(categoriasCargadas)
            return
        }

        viewModelScope.launch {
            _estado.value = EstadoBusqueda.CargandoCategorias
            when (val resultado = repositorioCategorias.obtenerCategorias()) {
                is Resultado.Exito -> {
                    categoriasCargadas = resultado.datos
                    _estado.value = EstadoBusqueda.ListoCategorias(resultado.datos)
                }
                is Resultado.Error -> {
                    _estado.value = EstadoBusqueda.Error(resultado.mensaje)
                }
                is Resultado.Cargando -> {
                    _estado.value = EstadoBusqueda.CargandoCategorias
                }
            }
        }
    }

    fun buscarSugerencias(query: String) {
        trabajoBusqueda?.cancel()

        val texto = query.trim()
        if (texto.length < 2) {
            if (categoriasCargadas.isNotEmpty()) {
                _estado.value = EstadoBusqueda.ListoCategorias(categoriasCargadas)
            } else {
                cargarCategorias()
            }
            return
        }

        trabajoBusqueda = viewModelScope.launch {
            delay(300)
            when (val resultado = repositorioProductos.buscar(nombre = texto, idCategoria = null)) {
                is Resultado.Exito -> {
                    val sugerenciasUnicas = resultado.datos
                        .distinctBy { Pair(it.nombreProducto.lowercase(), it.idCategoria) }
                        .take(8)

                    val sinCoincidencias = sugerenciasUnicas.isEmpty()
                    _estado.value = EstadoBusqueda.Sugerencias(
                        sugerencias = sugerenciasUnicas,
                        sinCoincidencias = sinCoincidencias
                    )
                }
                is Resultado.Error -> {
                    _estado.value = EstadoBusqueda.Error(resultado.mensaje)
                }
                is Resultado.Cargando -> {}
            }
        }
    }

    fun buscarResultados(nombre: String?, idCategoria: Int?, terminoMostrar: String) {
        viewModelScope.launch {
            _estadoResultados.value = EstadoResultadosBusqueda.Cargando
            val catId = if (idCategoria == null || idCategoria == -1) null else idCategoria
            val nom = if (nombre.isNullOrBlank()) null else nombre.trim()

            when (val resultado = repositorioProductos.buscar(nombre = nom, idCategoria = catId)) {
                is Resultado.Exito -> {
                    val listaProductos = resultado.datos
                    if (listaProductos.isEmpty()) {
                        _estadoResultados.value = EstadoResultadosBusqueda.SinResultados("No hay puestos con ese producto")
                    } else {
                        val productosPorPuesto = listaProductos
                            .groupBy { it.idPuesto }
                            .mapNotNull { (_, productosPuesto) ->
                                productosPuesto.minByOrNull { it.precio }
                            }

                        _estadoResultados.value = EstadoResultadosBusqueda.Exito(
                            terminoBuscado = terminoMostrar,
                            productosAgrupados = productosPorPuesto
                        )
                    }
                }
                is Resultado.Error -> {
                    _estadoResultados.value = EstadoResultadosBusqueda.Error(resultado.mensaje)
                }
                is Resultado.Cargando -> {
                    _estadoResultados.value = EstadoResultadosBusqueda.Cargando
                }
            }
        }
    }

    fun registrarBusqueda(termino: String, idCategoria: Int?) {
        viewModelScope.launch {
            repositorioProductos.registrarBusqueda(
                SolicitudBusqueda(
                    termino = termino,
                    idCategoria = idCategoria?.takeIf { it != -1 },
                )
            )
        }
    }

    fun limpiarResultados() {
        _estadoResultados.value = EstadoResultadosBusqueda.Inicial
    }
}
