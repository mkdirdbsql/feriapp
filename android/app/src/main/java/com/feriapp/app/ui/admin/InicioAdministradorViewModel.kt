package com.feriapp.app.ui.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feriapp.app.data.local.GestorSesion
import com.feriapp.app.data.model.Comerciante
import com.feriapp.app.data.model.Reporte
import com.feriapp.app.data.repository.RepositorioAdministracion
import com.feriapp.app.util.Resultado
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class EstadoInicioAdmin {
    data object Cargando : EstadoInicioAdmin()
    data class Listo(val comerciantes: List<Comerciante>, val reporte: Reporte) : EstadoInicioAdmin()
    data class Error(val mensaje: String, val esSesionExpirada: Boolean = false) : EstadoInicioAdmin()
}

class InicioAdministradorViewModel(
    private val repositorioAdmin: RepositorioAdministracion,
    private val gestorSesion: GestorSesion,
    contexto: Context
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoInicioAdmin>(EstadoInicioAdmin.Cargando)
    val estado: StateFlow<EstadoInicioAdmin> = _estado.asStateFlow()

    var idComercianteSeleccionado: Int? = null
    var formatoReporteSeleccionado: String = "PDF"

    init {
        cargarDatos()
    }

    fun cargarDatos() {
        viewModelScope.launch {
            _estado.value = EstadoInicioAdmin.Cargando

            val defComerciantes = async { repositorioAdmin.listarComerciantes() }
            val defReporte = async { repositorioAdmin.obtenerReportes() }

            val resComerciantes = defComerciantes.await()
            val resReporte = defReporte.await()

            if (resComerciantes is Resultado.Error) {
                val es401 = resComerciantes.codigo == 401 || resComerciantes.codigo == 403
                _estado.value = EstadoInicioAdmin.Error(resComerciantes.mensaje, esSesionExpirada = es401)
                return@launch
            }

            if (resReporte is Resultado.Error) {
                val es401 = resReporte.codigo == 401 || resReporte.codigo == 403
                _estado.value = EstadoInicioAdmin.Error(resReporte.mensaje, esSesionExpirada = es401)
                return@launch
            }

            if (resComerciantes is Resultado.Exito && resReporte is Resultado.Exito) {
                val comerciantes = resComerciantes.datos
                val reporte = resReporte.datos
                _estado.value = EstadoInicioAdmin.Listo(comerciantes, reporte)
            }
        }
    }

    fun reintentar() {
        cargarDatos()
    }

    suspend fun cerrarSesion() {
        gestorSesion.cerrarSesion()
    }
}
