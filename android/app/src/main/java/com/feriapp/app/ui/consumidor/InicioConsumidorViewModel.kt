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

data class DatosDetallePanel(
    val idPuesto: Int,
    val idProducto: Int,
    val titulo: String,
    val descripcion: String?,
    val precioTexto: String?,
    val disponibleTexto: String?,
    val fotos: List<String>,
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
    var modoBusquedaActivo: Boolean = false
    var modoResultadosActivo: Boolean = false
    var textoBusqueda: String = ""

    var camaraAntesDeBuscar: CamaraMapa? = null
    var camaraAlSalir: CamaraMapa? = null
    var busquedaNuevaRealizada: Boolean = false

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
            if (idProducto == -1) {
                when (val resultado = repositorioPuestos.obtener(idPuesto)) {
                    is Resultado.Exito -> {
                        val puesto = resultado.datos
                        val fotos = if (!puesto.imagenUrl.isNullOrBlank()) listOf(puesto.imagenUrl) else emptyList()
                        val datos = DatosDetallePanel(
                            idPuesto = puesto.idPuesto,
                            idProducto = -1,
                            titulo = puesto.nombrePuesto,
                            descripcion = puesto.descripcion,
                            precioTexto = null,
                            disponibleTexto = null,
                            fotos = fotos,
                            latitud = puesto.latitud,
                            longitud = puesto.longitud,
                            esProductoConcreto = false
                        )
                        _estadoDetalle.value = EstadoDetalle.Listo(datos)
                    }
                    is Resultado.Error -> {
                        _estadoDetalle.value = EstadoDetalle.Error(resultado.mensaje)
                    }
                    is Resultado.Cargando -> {
                        _estadoDetalle.value = EstadoDetalle.Cargando
                    }
                }
            } else {
                when (val resultado = repositorioProductos.obtenerDePuesto(idPuesto)) {
                    is Resultado.Exito -> {
                        val producto = resultado.datos.find { it.idProducto == idProducto }
                        if (producto != null) {
                            val fotos = producto.imagenes.mapNotNull { it.url }
                            val precioTexto = if (producto.precio % 1.0 == 0.0) {
                                "Precio: ${producto.precio.toInt()} Bs"
                            } else {
                                String.format(Locale.getDefault(), "Precio: %.2f Bs", producto.precio)
                            }
                            val disponibleTexto = if (producto.disponible) "Disponible: Sí" else "Disponible: No"

                            var lat = 0.0
                            var lng = 0.0
                            when (val resPuesto = repositorioPuestos.obtener(idPuesto)) {
                                is Resultado.Exito -> {
                                    lat = resPuesto.datos.latitud
                                    lng = resPuesto.datos.longitud
                                }
                                else -> {}
                            }

                            val datos = DatosDetallePanel(
                                idPuesto = idPuesto,
                                idProducto = idProducto,
                                titulo = producto.nombreProducto,
                                descripcion = producto.descripcion,
                                precioTexto = precioTexto,
                                disponibleTexto = disponibleTexto,
                                fotos = fotos,
                                latitud = lat,
                                longitud = lng,
                                esProductoConcreto = true
                            )
                            _estadoDetalle.value = EstadoDetalle.Listo(datos)
                        } else {
                            _estadoDetalle.value = EstadoDetalle.Error("Producto no encontrado.")
                        }
                    }
                    is Resultado.Error -> {
                        _estadoDetalle.value = EstadoDetalle.Error(resultado.mensaje)
                    }
                    is Resultado.Cargando -> {
                        _estadoDetalle.value = EstadoDetalle.Cargando
                    }
                }
            }
        }
    }

    fun cerrarDetalle() {
        _estadoDetalle.value = EstadoDetalle.Oculto
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
