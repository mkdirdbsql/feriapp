package com.feriapp.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feriapp.app.data.repository.RepositorioAutenticacion
import com.feriapp.app.util.Resultado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class EstadoRegistro {
    data object Inicial : EstadoRegistro()
    data object Cargando : EstadoRegistro()
    data object Exito : EstadoRegistro()
    data class Error(val mensaje: String) : EstadoRegistro()
}

class RegistroViewModel(
    private val repositorioAutenticacion: RepositorioAutenticacion,
) : ViewModel() {

    private val _estado = MutableStateFlow<EstadoRegistro>(EstadoRegistro.Inicial)
    val estado: StateFlow<EstadoRegistro> = _estado.asStateFlow()

    fun registrarComerciante(
        nombre: String,
        apellido: String,
        telefono: String,
        contrasena: String,
        nombreComercial: String,
        descripcionComercio: String?,
    ) {
        viewModelScope.launch {
            _estado.value = EstadoRegistro.Cargando
            val resultado = repositorioAutenticacion.registrarComerciante(
                nombre = nombre,
                apellido = apellido,
                telefono = telefono,
                contrasena = contrasena,
                nombreComercial = nombreComercial,
                descripcionComercio = descripcionComercio.takeIf { !it.isNullOrBlank() },
            )
            when (resultado) {
                is Resultado.Exito -> {
                    _estado.value = EstadoRegistro.Exito
                }
                is Resultado.Error -> {
                    _estado.value = EstadoRegistro.Error(resultado.mensaje)
                }
                is Resultado.Cargando -> {
                    _estado.value = EstadoRegistro.Cargando
                }
            }
        }
    }

    fun restablecerEstado() {
        _estado.value = EstadoRegistro.Inicial
    }
}
