package com.feriapp.app.ui.consumidor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feriapp.app.data.model.Puesto
import com.feriapp.app.data.repository.RepositorioPuestos
import com.feriapp.app.util.Resultado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CamaraMapa(
    val latitud: Double,
    val longitud: Double,
    val zoom: Double
)

sealed class EstadoInicioConsumidor {
    data object Cargando : EstadoInicioConsumidor()
    data class Exito(val puestos: List<Puesto>) : EstadoInicioConsumidor()
    data class Error(val mensaje: String) : EstadoInicioConsumidor()
}

class InicioConsumidorViewModel(
    private val repositorioPuestos: RepositorioPuestos,
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoInicioConsumidor>(EstadoInicioConsumidor.Cargando)
    val estado: StateFlow<EstadoInicioConsumidor> = _estado.asStateFlow()

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
}
