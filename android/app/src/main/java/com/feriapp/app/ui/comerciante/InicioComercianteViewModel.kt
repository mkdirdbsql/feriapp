package com.feriapp.app.ui.comerciante

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feriapp.app.data.model.PerfilComerciante
import com.feriapp.app.data.model.PuestoPerfil
import com.feriapp.app.data.remote.dto.SolicitudCrearPuesto
import com.feriapp.app.data.repository.RepositorioComerciante
import com.feriapp.app.data.repository.RepositorioPuestos
import com.feriapp.app.ui.consumidor.CamaraMapa
import com.feriapp.app.util.Resultado
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
    data class Error(val mensaje: String) : EstadoGuardadoPuesto()
}

class InicioComercianteViewModel(
    private val repositorioComerciante: RepositorioComerciante,
    private val repositorioPuestos: RepositorioPuestos,
    contexto: Context
) : ViewModel() {

    private val contextoAplicacion = contexto.applicationContext

    private val _estado = MutableStateFlow<EstadoInicioComerciante>(EstadoInicioComerciante.Cargando)
    val estado: StateFlow<EstadoInicioComerciante> = _estado.asStateFlow()

    private val _estadoGuardado = MutableStateFlow<EstadoGuardadoPuesto>(EstadoGuardadoPuesto.Inactivo)
    val estadoGuardado: StateFlow<EstadoGuardadoPuesto> = _estadoGuardado.asStateFlow()

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
                        _estado.value = EstadoInicioComerciante.SinPuesto(perfil)
                    } else {
                        _estado.value = EstadoInicioComerciante.ConPuesto(
                            perfil = perfil,
                            puesto = perfil.puesto,
                            cantidadProductos = perfil.productos.size
                        )
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
                    recargarPerfil()
                }
                is Resultado.Error -> {
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

    fun resetearEstadoGuardado() {
        _estadoGuardado.value = EstadoGuardadoPuesto.Inactivo
    }
}
