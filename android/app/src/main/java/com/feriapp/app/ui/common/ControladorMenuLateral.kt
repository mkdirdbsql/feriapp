package com.feriapp.app.ui.common

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import com.feriapp.app.R
import com.feriapp.app.data.local.GestorPreferencias
import com.feriapp.app.data.local.GestorSesion
import com.feriapp.app.data.repository.RepositorioAutenticacion
import com.feriapp.app.databinding.MenuLateralBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ControladorMenuLateral(
    private val contexto: Context,
    private val drawerLayout: DrawerLayout,
    private val bindingMenu: MenuLateralBinding,
    private val gestorSesion: GestorSesion,
    private val gestorPreferencias: GestorPreferencias,
    private val repositorioAutenticacion: RepositorioAutenticacion,
    private val coroutineScope: CoroutineScope,
    private val navController: NavController,
    private val alCambiarModoOscuro: ((Boolean) -> Unit)? = null,
) {

    init {
        configurarMenu()
    }

    private fun configurarMenu() {
        coroutineScope.launch {
            val modoOscuroActivo = gestorPreferencias.obtenerModoOscuro()
            bindingMenu.switchModoOscuro.isChecked = modoOscuroActivo
        }

        bindingMenu.btnCerrarMenu.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        bindingMenu.btnCambiarCuenta.setOnClickListener {
            procesarCambioCuentaOInicio()
        }

        bindingMenu.btnVolverInicioMenu.setOnClickListener {
            procesarCambioCuentaOInicio()
        }

        bindingMenu.btnMasInformacion.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            navController.navigate(R.id.accionGlobalAMasInformacion)
        }

        bindingMenu.switchModoOscuro.setOnCheckedChangeListener { _, isChecked ->
            coroutineScope.launch {
                gestorPreferencias.guardarModoOscuro(isChecked)
                AppCompatDelegate.setDefaultNightMode(
                    if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_NO
                )
                alCambiarModoOscuro?.invoke(isChecked)
            }
        }
    }

    private fun procesarCambioCuentaOInicio() {
        drawerLayout.closeDrawer(GravityCompat.START)
        coroutineScope.launch {
            val sesion = gestorSesion.obtenerSesion()
            if (sesion != null) {
                mostrarDialogoCerrarSesion()
            } else {
                navController.navigate(R.id.accionGlobalASeleccionUsuario)
            }
        }
    }

    private fun mostrarDialogoCerrarSesion() {
        MaterialAlertDialogBuilder(contexto)
            .setTitle(R.string.titulo_cerrar_sesion)
            .setMessage(R.string.mensaje_cerrar_sesion)
            .setPositiveButton(R.string.btn_cerrar_sesion) { dialog, _ ->
                dialog.dismiss()
                coroutineScope.launch {
                    repositorioAutenticacion.cerrarSesion()
                    navController.navigate(R.id.accionGlobalASeleccionUsuarioLimpiandoPila)
                }
            }
            .setNegativeButton(R.string.btn_cancelar) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
