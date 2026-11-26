package com.feriapp.app.ui.consumidor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feriapp.app.data.model.ProductoBuscado
import com.feriapp.app.data.model.Puesto
import com.feriapp.app.data.repository.RepositorioProductos
import com.feriapp.app.data.repository.RepositorioPuestos
import com.feriapp.app.data.repository.RepositorioRutas
import com.feriapp.app.util.Resultado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.util.Locale

data class CamaraMapa(
    val latitud: Double,
    val longitud: Double,
    val zoom: Double
)

data class ProductoDetalle(
    val idProducto: Int,
    val nombre: String,
    val descripcion: String?,
    val precioTexto: String,
    val disponibleTexto: String,
    val fotoUrl: String?
)

data class DatosDetallePanel(
    val idPuesto: Int,
    val idProducto: Int,
    val titulo: String,
    val productos: List<ProductoDetalle>,
    val indiceInicial: Int,
    val latitud: Double,
    val longitud: Double,
    val esProductoConcreto: Boolean
)

sealed class EstadoInicioConsumidor {
    data object Cargando : EstadoInicioConsumidor()
    data class Exito(val puestos: List<Puesto>) : EstadoInicioConsumidor()
    data class Error(val mensaje: String) : EstadoInicioConsumidor()
}

sealed class EstadoDetalle {
    data object Oculto : EstadoDetalle()
    data object Cargando : EstadoDetalle()
    data class Listo(val datos: DatosDetallePanel) : EstadoDetalle()
    data class Error(val mensaje: String) : EstadoDetalle()
}

sealed class EstadoRuta {
    data object Inactivo : EstadoRuta()
    data object Calculando : EstadoRuta()
    data class Exito(val puntos: List<GeoPoint>) : EstadoRuta()
    data class FalloLineaRecta(val origen: GeoPoint, val destino: GeoPoint, val motivo: String) : EstadoRuta()
}

class InicioConsumidorViewModel(
    private val repositorioPuestos: RepositorioPuestos,
    private val repositorioProductos: RepositorioProductos,
    private val repositorioRutas: RepositorioRutas,
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoInicioConsumidor>(EstadoInicioConsumidor.Cargando)
    val estado: StateFlow<EstadoInicioConsumidor> = _estado.asStateFlow()

    private val _estadoDetalle = MutableStateFlow<EstadoDetalle>(EstadoDetalle.Oculto)
    val estadoDetalle: StateFlow<EstadoDetalle> = _estadoDetalle.asStateFlow()

    private val _estadoRuta = MutableStateFlow<EstadoRuta>(EstadoRuta.Inactivo)
    val estadoRuta: StateFlow<EstadoRuta> = _estadoRuta.asStateFlow()

    var ubicacionActiva: Boolean = false
    var menuAbierto: Boolean = false

    var puestoNormalSeleccionado: Puesto? = null
    var productoResultadoSeleccionado: ProductoBuscado? = null
    var marcadorSeleccionadoId: Int? = null

    var modoBusquedaActivo: Boolean = false
    var modoResultadosActivo: Boolean = false
    var textoBusqueda: String = ""
    var busquedaPorNombre: Boolean = false

    var camaraAntesDeBuscar: CamaraMapa? = null
    var camaraAlSalir: CamaraMapa? = null
    var busquedaNuevaRealizada: Boolean = false

    var indiceProductoDetalleActual: Int = 0

    init {
        cargarPuestos()
    }

    fun cargarPuestos() {
        viewModelScope.launch {
            _estado.value = EstadoInicioConsumidor.Cargando
            when (val resultado = repositorioPuestos.listar()) {
                is Resultado.Exito -> {
                    _estado.value = EstadoInicioConsumidor.Exito(resultado.datos)
                }
                is Resultado.Error -> {
                    _estado.value = EstadoInicioConsumidor.Error(resultado.mensaje)
                }
                is Resultado.Cargando -> {
                    _estado.value = EstadoInicioConsumidor.Cargando
                }
            }
        }
    }

    fun cargarDetalle(idPuesto: Int, idProducto: Int) {
        viewModelScope.launch {
            _estadoDetalle.value = EstadoDetalle.Cargando

            var tituloPuesto = ""
            var lat = 0.0
            var lng = 0.0

            when (val resPuesto = repositorioPuestos.obtener(idPuesto)) {
                is Resultado.Exito -> {
                    val puesto = resPuesto.datos
                    tituloPuesto = puesto.nombrePuesto
                    lat = puesto.latitud
                    lng = puesto.longitud
                }
                else -> {}
            }

            val productosDetalle = mutableListOf<ProductoDetalle>()
            when (val resProductos = repositorioProductos.obtenerDePuesto(idPuesto)) {
                is Resultado.Exito -> {
                    resProductos.datos.forEach { p ->
                        val precioTexto = if (p.precio % 1.0 == 0.0) {
                            "Precio: ${p.precio.toInt()} Bs"
                        } else {
                            String.format(Locale.getDefault(), "Precio: %.2f Bs", p.precio)
                        }
                        val dispTexto = if (p.disponible) "Disponible: Sí" else "Disponible: No"
                        val urlFoto = p.imagenes.firstOrNull()?.url

                        productosDetalle.add(
                            ProductoDetalle(
                                idProducto = p.idProducto,
                                nombre = p.nombreProducto,
                                descripcion = p.descripcion,
                                precioTexto = precioTexto,
                                disponibleTexto = dispTexto,
                                fotoUrl = urlFoto
                            )
                        )
                    }
                }
                else -> {}
            }

            val esProductoConcreto = (idProducto != -1)
            val idxInicial = if (esProductoConcreto) {
                val enc = productosDetalle.indexOfFirst { it.idProducto == idProducto }
                if (enc != -1) enc else 0
            } else {
                0
            }

            indiceProductoDetalleActual = idxInicial

            val datos = DatosDetallePanel(
                idPuesto = idPuesto,
                idProducto = idProducto,
                titulo = tituloPuesto,
                productos = productosDetalle,
                indiceInicial = idxInicial,
                latitud = lat,
                longitud = lng,
                esProductoConcreto = esProductoConcreto
            )

            _estadoDetalle.value = EstadoDetalle.Listo(datos)
        }
    }

    fun cerrarDetalle() {
        _estadoDetalle.value = EstadoDetalle.Oculto
        indiceProductoDetalleActual = 0
    }

    fun trazarRuta(origen: GeoPoint, destino: GeoPoint) {
        viewModelScope.launch {
            _estadoRuta.value = EstadoRuta.Calculando
            when (val resultado = repositorioRutas.calcularRutaPie(origen, destino)) {
                is Resultado.Exito -> {
                    _estadoRuta.value = EstadoRuta.Exito(resultado.datos)
                }
                is Resultado.Error -> {
                    _estadoRuta.value = EstadoRuta.FalloLineaRecta(origen, destino, resultado.mensaje)
                }
                is Resultado.Cargando -> {
                    _estadoRuta.value = EstadoRuta.Calculando
                }
            }
        }
    }

    fun limpiarRuta() {
        _estadoRuta.value = EstadoRuta.Inactivo
    }
}
