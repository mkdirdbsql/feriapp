package com.feriapp.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.feriapp.app.R
import com.feriapp.app.data.local.GestorSesion
import com.feriapp.app.databinding.FragmentoInicioAdministradorBinding
import kotlinx.coroutines.launch

class FragmentoInicioAdministrador : Fragment() {

    private var _binding: FragmentoInicioAdministradorBinding? = null
    private val binding get() = _binding!!

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

        binding.btnEditarComerciante.setOnClickListener {
            val paqueteArgs = bundleOf("idComerciante" to 42)
            findNavController().navigate(R.id.accionInicioAdministradorAEdicionUsuario, paqueteArgs)
        }

        binding.btnCerrarSesionAdmin.setOnClickListener {
            val gestorSesion = GestorSesion(requireContext())
            lifecycleScope.launch {
                gestorSesion.cerrarSesion()
                findNavController().navigate(R.id.accionInicioAdministradorASeleccionUsuario)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
