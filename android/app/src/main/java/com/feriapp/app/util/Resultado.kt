package com.feriapp.app.util

sealed class Resultado<out T> {
    data class Exito<out T>(val datos: T) : Resultado<T>()
    data class Error(val mensaje: String, val codigo: Int? = null) : Resultado<Nothing>()
    data object Cargando : Resultado<Nothing>()
}
