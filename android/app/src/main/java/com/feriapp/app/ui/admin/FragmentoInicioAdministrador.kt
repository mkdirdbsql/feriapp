package com.feriapp.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.feriapp.app.AplicacionFeria
import com.feriapp.app.R
import com.feriapp.app.data.model.Comerciante
import com.feriapp.app.data.model.Reporte
import com.feriapp.app.databinding.FragmentoInicioAdministradorBinding
import com.feriapp.app.ui.common.FragmentoMapa
import com.feriapp.app.ui.common.MarcadorMapa
import com.feriapp.app.ui.common.fabricaViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FragmentoInicioAdministrador : Fragment() {

    private var _binding: FragmentoInicioAdministradorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InicioAdministradorViewModel by viewModels {
        fabricaViewModel {
            val contenedor = (requireActivity().application as AplicacionFeria).contenedorApp
            InicioAdministradorViewModel(
                contenedor.repositorioAdministracion,
                contenedor.gestorSesion,
                requireContext().applicationContext
            )
        }
    }

    private var fragmentoMapa: FragmentoMapa? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentoInicioAdministradorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentoMapa = childFragmentManager.findFragmentById(R.id.contenedorMapaAdmin) as? FragmentoMapa

        configurarNavegacion()
        configurarControlesZoom()
        configurarToggleFormato()
        configurarBotonAtras()
        iniciarReloj()
        observarResultadoEdicionUsuario()
        observarEstadoViewModel()
    }

    private fun configurarNavegacion() {
        binding.btnVerUsuarioAdmin.setOnClickListener {
            val id = viewModel.idComercianteSeleccionado ?: return@setOnClickListener
            findNavController().navigate(
                R.id.accionInicioAdministradorAEdicionUsuario,
                bundleOf("idComerciante" to id)
            )
        }

        binding.btnGenerarReporteAdmin.setOnClickListener {
            Toast.makeText(requireContext(), R.string.toast_agregar_puesto_proximamente, Toast.LENGTH_SHORT).apply {
                setText("Próximamente")
            }.show()
        }

        binding.btnSalirAdmin.setOnClickListener {
            confirmarSalirAdmin()
        }

        binding.btnReintentarAdmin.setOnClickListener {
            viewModel.reintentar()
        }
    }

    private fun configurarControlesZoom() {
        binding.btnAcercarAdmin.setOnClickListener {
            fragmentoMapa?.acercar()
        }
        binding.btnAlejarAdmin.setOnClickListener {
            fragmentoMapa?.alejar()
        }
    }

    private fun configurarToggleFormato() {
        if (viewModel.formatoReporteSeleccionado == "Word") {
            binding.toggleGroupFormatoReporte.check(R.id.btnFormatoWord)
        } else {
            binding.toggleGroupFormatoReporte.check(R.id.btnFormatoPdf)
        }

        binding.toggleGroupFormatoReporte.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnFormatoWord -> viewModel.formatoReporteSeleccionado = "Word"
                    else -> viewModel.formatoReporteSeleccionado = "PDF"
                }
            }
        }
    }

    private fun configurarBotonAtras() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    confirmarSalirAdmin()
                }
            }
        )
    }

    private fun iniciarReloj() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val ahora = Calendar.getInstance().time
                    val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val formatoHora = SimpleDateFormat("hh:mm a", Locale.getDefault())

                    binding.tvFechaAdmin.text = getString(R.string.formato_fecha_admin, formatoFecha.format(ahora))
                    binding.tvHoraAdmin.text = getString(R.string.formato_hora_admin, formatoHora.format(ahora))

                    delay(10000) // Actualizar periódicamente
                }
            }
        }
    }

    private fun observarResultadoEdicionUsuario() {
        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Boolean>("usuario_modificado")
            ?.observe(viewLifecycleOwner) { modificado ->
                if (modificado == true) {
                    viewModel.cargarDatos()
                    findNavController().currentBackStackEntry?.savedStateHandle?.remove<Boolean>("usuario_modificado")
                }
            }
    }

    private fun observarEstadoViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect { estado ->
                    when (estado) {
                        is EstadoInicioAdmin.Cargando -> {
                            mostrarCargando()
                        }
                        is EstadoInicioAdmin.Listo -> {
                            mostrarListo(estado.comerciantes, estado.reporte)
                        }
                        is EstadoInicioAdmin.Error -> {
                            if (estado.esSesionExpirada) {
                                Toast.makeText(requireContext(), R.string.msg_sesion_expirada, Toast.LENGTH_LONG).show()
                                viewLifecycleOwner.lifecycleScope.launch {
                                    viewModel.cerrarSesion()
                                    findNavController().navigate(R.id.accionInicioAdministradorASeleccionUsuario)
                                }
                            } else {
                                mostrarError(estado.mensaje)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun mostrarCargando() {
        binding.indicadorCargandoAdmin.visibility = View.VISIBLE
        binding.tarjetaErrorAdmin.visibility = View.GONE
        binding.btnVerUsuarioAdmin.isEnabled = false
    }

    private fun mostrarListo(comerciantes: List<Comerciante>, reporte: Reporte) {
        binding.indicadorCargandoAdmin.visibility = View.GONE
        binding.tarjetaErrorAdmin.visibility = View.GONE

        // Configurar mapa y marcadores
        fragmentoMapa?.alSeleccionarMarcador = { id, datos ->
            if (datos is Comerciante) {
                viewModel.idComercianteSeleccionado = id
                val estadoActual = viewModel.estado.value
                if (estadoActual is EstadoInicioAdmin.Listo) {
                    actualizarInterfaceUsuario(estadoActual.comerciantes, estadoActual.reporte)
                }
            } else {
                viewModel.idComercianteSeleccionado = null
                val estadoActual = viewModel.estado.value
                if (estadoActual is EstadoInicioAdmin.Listo) {
                    actualizarInterfaceUsuario(estadoActual.comerciantes, estadoActual.reporte)
                }
            }
        }

        actualizarInterfaceUsuario(comerciantes, reporte)
    }

    private fun actualizarInterfaceUsuario(comerciantes: List<Comerciante>, reporte: Reporte) {
        val marcadores = comerciantes.filter { it.latitud != null && it.longitud != null }.map { c ->
            val esSeleccionado = c.idComerciante == viewModel.idComercianteSeleccionado
            val iconoRes = if (esSeleccionado) {
                R.drawable.ic_usuario_resaltado
            } else if (c.estado.equals("activo", ignoreCase = true)) {
                R.drawable.ic_usuario_activo
            } else {
                R.drawable.ic_usuario_inactivo
            }

            MarcadorMapa(
                id = c.idComerciante,
                latitud = c.latitud!!,
                longitud = c.longitud!!,
                titulo = c.nombrePuesto ?: c.nombreComercial,
                subtitulo = "${c.nombre} ${c.apellido}",
                icono = iconoRes,
                datosOriginales = c
            )
        }
        fragmentoMapa?.mostrarMarcadores(marcadores)

        val cSel = comerciantes.find { it.idComerciante == viewModel.idComercianteSeleccionado }
        if (cSel != null) {
            binding.tvUsuarioAdmin.text = getString(R.string.formato_panel_usuario, cSel.nombre, cSel.apellido, cSel.idComerciante)
            binding.tvPuestoAdmin.text = getString(R.string.formato_panel_puesto, cSel.nombrePuesto ?: getString(R.string.sin_puesto))
            binding.btnVerUsuarioAdmin.isEnabled = true
        } else {
            binding.tvUsuarioAdmin.text = getString(R.string.panel_usuario_no_seleccionado)
            binding.tvPuestoAdmin.text = ""
            binding.btnVerUsuarioAdmin.isEnabled = false
        }

        val catMasBuscada = reporte.categoriaMasBuscada
        if (catMasBuscada != null) {
            binding.tvProductoMasBuscadoAdmin.text = getString(
                R.string.formato_producto_mas_buscado,
                catMasBuscada.categoria,
                catMasBuscada.cantidad
            )
        } else {
            binding.tvProductoMasBuscadoAdmin.text = getString(R.string.producto_mas_buscado_sin_datos)
        }
    }

    private fun mostrarError(mensaje: String) {
        binding.indicadorCargandoAdmin.visibility = View.GONE
        binding.tarjetaErrorAdmin.visibility = View.VISIBLE
        binding.tvMensajeErrorAdmin.text = mensaje
    }

    private fun confirmarSalirAdmin() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.titulo_dialogo_salir_admin)
            .setMessage(R.string.mensaje_dialogo_salir_admin)
            .setPositiveButton(R.string.btn_salir) { dialog, _ ->
                dialog.dismiss()
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.cerrarSesion()
                    findNavController().navigate(R.id.accionInicioAdministradorASeleccionUsuario)
                }
            }
            .setNegativeButton(R.string.btn_cancelar) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
