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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class EstadoInicioAdmin {
    data object Cargando : EstadoInicioAdmin()
    data class Listo(val comerciantes: List<Comerciante>, val reporte: Reporte) : EstadoInicioAdmin()
    data class Error(val mensaje: String, val esSesionExpirada: Boolean = false) : EstadoInicioAdmin()
}

sealed class EstadoReporteAdmin {
    data object Inactivo : EstadoReporteAdmin()
    data object Generando : EstadoReporteAdmin()
    data class Listo(val archivo: File, val mimeType: String, val consumido: Boolean = false) : EstadoReporteAdmin()
    data class Error(val mensaje: String, val esSesionExpirada: Boolean = false) : EstadoReporteAdmin()
}

class InicioAdministradorViewModel(
    private val repositorioAdmin: RepositorioAdministracion,
    private val gestorSesion: GestorSesion,
    contexto: Context
) : ViewModel() {

    private val contextoAplicacion = contexto.applicationContext

    private val _estado = MutableStateFlow<EstadoInicioAdmin>(EstadoInicioAdmin.Cargando)
    val estado: StateFlow<EstadoInicioAdmin> = _estado.asStateFlow()

    private val _estadoReporte = MutableStateFlow<EstadoReporteAdmin>(EstadoReporteAdmin.Inactivo)
    val estadoReporte: StateFlow<EstadoReporteAdmin> = _estadoReporte.asStateFlow()

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

    fun generarReporte(formatoTexto: String) {
        val formato = if (formatoTexto.equals("Word", ignoreCase = true)) "word" else "pdf"
        val extension = if (formato == "word") ".docx" else ".pdf"
        val mimeType = if (formato == "word") {
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        } else {
            "application/pdf"
        }

        viewModelScope.launch {
            _estadoReporte.value = EstadoReporteAdmin.Generando

            val carpetaReportes = File(contextoAplicacion.cacheDir, "reportes").apply { mkdirs() }

            // Limpieza de reportes viejos (más de 24 horas)
            val ahora = System.currentTimeMillis()
            val limite24h = 24 * 60 * 60 * 1000L
            carpetaReportes.listFiles()?.forEach { archivo ->
                if (ahora - archivo.lastModified() > limite24h) {
                    archivo.delete()
                }
            }

            val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
            val nombreArchivo = "reporte-feriapp-$timestamp$extension"
            val archivoDestino = File(carpetaReportes, nombreArchivo)

            when (val res = repositorioAdmin.descargarReporte(formato, archivoDestino)) {
                is Resultado.Exito -> {
                    _estadoReporte.value = EstadoReporteAdmin.Listo(
                        archivo = res.datos,
                        mimeType = mimeType,
                        consumido = false
                    )
                }
                is Resultado.Error -> {
                    val es401 = res.codigo == 401 || res.codigo == 403
                    _estadoReporte.value = EstadoReporteAdmin.Error(res.mensaje, esSesionExpirada = es401)
                }
                is Resultado.Cargando -> {
                    _estadoReporte.value = EstadoReporteAdmin.Generando
                }
            }
        }
    }

    fun consumirReporte() {
        val actual = _estadoReporte.value
        if (actual is EstadoReporteAdmin.Listo) {
            _estadoReporte.value = actual.copy(consumido = true)
        } else if (actual is EstadoReporteAdmin.Error) {
            _estadoReporte.value = EstadoReporteAdmin.Inactivo
        }
    }

    fun reintentar() {
        cargarDatos()
    }

    suspend fun cerrarSesion() {
        gestorSesion.cerrarSesion()
    }
}
