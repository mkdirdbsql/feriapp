package com.feriapp.app.ui.comerciante

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.feriapp.app.R
import com.feriapp.app.data.local.GestorSesion
import com.feriapp.app.databinding.FragmentoInicioComercianteBinding
import kotlinx.coroutines.launch

class FragmentoInicioComerciante : Fragment() {

    private var _binding: FragmentoInicioComercianteBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentoInicioComercianteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAgregarPuesto.setOnClickListener {
            findNavController().navigate(R.id.accionInicioComercianteAAgregarPuesto)
        }

        binding.btnAgregarProducto.setOnClickListener {
            findNavController().navigate(R.id.accionInicioComercianteAAgregarProducto)
        }

        binding.btnCerrarSesionComerciante.setOnClickListener {
            val gestorSesion = GestorSesion(requireContext())
            lifecycleScope.launch {
                gestorSesion.cerrarSesion()
                findNavController().navigate(R.id.accionInicioComercianteASeleccionUsuario)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
