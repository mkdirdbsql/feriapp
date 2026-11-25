package com.feriapp.app.ui.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feriapp.app.data.local.GestorSesion
import com.feriapp.app.data.model.DetalleComerciante
import com.feriapp.app.data.remote.dto.SolicitudEstado
import com.feriapp.app.data.repository.RepositorioAdministracion
import com.feriapp.app.util.Resultado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OpcionEstadoAdmin {
    Habilitado,
    Deshabilitado,
    Eliminar
}

sealed class EstadoEdicionUsuario {
    data object Cargando : EstadoEdicionUsuario()
    data class Listo(val detalle: DetalleComerciante) : EstadoEdicionUsuario()
    data class Error(val mensaje: String, val esSesionExpirada: Boolean = false, val esNoEncontrado: Boolean = false) : EstadoEdicionUsuario()
}

sealed class EstadoGuardadoAdmin {
    data object Inactivo : EstadoGuardadoAdmin()
    data object Guardando : EstadoGuardadoAdmin()
    data class ExitoEstado(val mensaje: String) : EstadoGuardadoAdmin()
    data object ExitoEliminado : EstadoGuardadoAdmin()
    data class Error(val mensaje: String) : EstadoGuardadoAdmin()
}

class EdicionUsuarioViewModel(
    private val repositorioAdmin: RepositorioAdministracion,
    private val gestorSesion: GestorSesion,
    contexto: Context,
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoEdicionUsuario>(EstadoEdicionUsuario.Cargando)
    val estado: StateFlow<EstadoEdicionUsuario> = _estado.asStateFlow()

    private val _estadoGuardado = MutableStateFlow<EstadoGuardadoAdmin>(EstadoGuardadoAdmin.Inactivo)
    val estadoGuardado: StateFlow<EstadoGuardadoAdmin> = _estadoGuardado.asStateFlow()

    var idComerciante: Int = -1
    var indiceProductoSeleccionado: Int = 0
    var indiceFotoProductoActual: Int = 0
    var opcionEstadoSeleccionada: OpcionEstadoAdmin = OpcionEstadoAdmin.Habilitado
    var estadoInicialServidor: OpcionEstadoAdmin = OpcionEstadoAdmin.Habilitado
    var cargadoInicialHecho: Boolean = false

    fun cargarDetalle(id: Int) {
        idComerciante = id
        viewModelScope.launch {
            _estado.value = EstadoEdicionUsuario.Cargando
            when (val res = repositorioAdmin.obtenerComerciante(id)) {
                is Resultado.Exito -> {
                    val detalle = res.datos
                    if (!cargadoInicialHecho) {
                        estadoInicialServidor = if (detalle.estado.equals("activo", ignoreCase = true)) {
                            OpcionEstadoAdmin.Habilitado
                        } else {
                            OpcionEstadoAdmin.Deshabilitado
                        }
                        opcionEstadoSeleccionada = estadoInicialServidor
                        cargadoInicialHecho = true
                    }
                    _estado.value = EstadoEdicionUsuario.Listo(detalle)
                }
                is Resultado.Error -> {
                    val es401 = res.codigo == 401 || res.codigo == 403
                    val es404 = res.codigo == 404
                    _estado.value = EstadoEdicionUsuario.Error(
                        mensaje = res.mensaje,
                        esSesionExpirada = es401,
                        esNoEncontrado = es404
                    )
                }
                is Resultado.Cargando -> {
                    _estado.value = EstadoEdicionUsuario.Cargando
                }
            }
        }
    }

    fun reintentar() {
        if (idComerciante != -1) {
            cargarDetalle(idComerciante)
        }
    }

    fun guardarEstado(nuevoEstadoStr: String) {
        if (idComerciante == -1) return
        viewModelScope.launch {
            _estadoGuardado.value = EstadoGuardadoAdmin.Guardando
            when (val res = repositorioAdmin.cambiarEstado(idComerciante, SolicitudEstado(nuevoEstadoStr))) {
                is Resultado.Exito -> {
                    estadoInicialServidor = if (nuevoEstadoStr.equals("activo", ignoreCase = true)) {
                        OpcionEstadoAdmin.Habilitado
                    } else {
                        OpcionEstadoAdmin.Deshabilitado
                    }
                    opcionEstadoSeleccionada = estadoInicialServidor
                    _estadoGuardado.value = EstadoGuardadoAdmin.ExitoEstado("Estado actualizado")
                    cargarDetalle(idComerciante)
                }
                is Resultado.Error -> {
                    _estadoGuardado.value = EstadoGuardadoAdmin.Error(res.mensaje)
                }
                is Resultado.Cargando -> {
                    _estadoGuardado.value = EstadoGuardadoAdmin.Guardando
                }
            }
        }
    }

    fun eliminarComerciante() {
        if (idComerciante == -1) return
        viewModelScope.launch {
            _estadoGuardado.value = EstadoGuardadoAdmin.Guardando
            when (val res = repositorioAdmin.eliminar(idComerciante)) {
                is Resultado.Exito -> {
                    _estadoGuardado.value = EstadoGuardadoAdmin.ExitoEliminado
                }
                is Resultado.Error -> {
                    _estadoGuardado.value = EstadoGuardadoAdmin.Error(res.mensaje)
                }
                is Resultado.Cargando -> {
                    _estadoGuardado.value = EstadoGuardadoAdmin.Guardando
                }
            }
        }
    }

    fun resetearEstadoGuardado() {
        _estadoGuardado.value = EstadoGuardadoAdmin.Inactivo
    }

    suspend fun cerrarSesion() {
        gestorSesion.cerrarSesion()
    }
}
