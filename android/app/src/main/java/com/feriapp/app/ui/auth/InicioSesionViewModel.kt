package com.feriapp.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feriapp.app.data.repository.RepositorioAutenticacion
import com.feriapp.app.util.Resultado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class EstadoInicioSesion {
    data object Inicial : EstadoInicioSesion()
    data object Cargando : EstadoInicioSesion()
    data class Exito(val rol: String) : EstadoInicioSesion()
    data class Error(val mensaje: String) : EstadoInicioSesion()
}

class InicioSesionViewModel(
    private val repositorioAutenticacion: RepositorioAutenticacion,
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoInicioSesion>(EstadoInicioSesion.Inicial)
    val estado: StateFlow<EstadoInicioSesion> = _estado.asStateFlow()

    fun iniciarSesion(identificador: String, contrasena: String) {
        if (identificador.isBlank() || contrasena.isBlank()) {
            _estado.value = EstadoInicioSesion.Error("Por favor, completa todos los campos.")
            return
        }

        viewModelScope.launch {
            _estado.value = EstadoInicioSesion.Cargando
            when (val resultado = repositorioAutenticacion.iniciarSesion(identificador, contrasena)) {
                is Resultado.Exito -> {
                    _estado.value = EstadoInicioSesion.Exito(resultado.datos.rol)
                }
                is Resultado.Error -> {
                    _estado.value = EstadoInicioSesion.Error(resultado.mensaje)
                }
                is Resultado.Cargando -> {
                    _estado.value = EstadoInicioSesion.Cargando
                }
            }
        }
    }

    fun restablecerEstado() {
        _estado.value = EstadoInicioSesion.Inicial
    }
}
