package com.feriapp.app.ui.splash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.feriapp.app.R
import com.feriapp.app.data.local.GestorSesion
import com.feriapp.app.databinding.FragmentoCargaBinding
import kotlinx.coroutines.launch

class FragmentoCarga : Fragment() {

    private var _binding: FragmentoCargaBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentoCargaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        verificarSesion()
    }

    private fun verificarSesion() {
        val gestorSesion = GestorSesion(requireContext())
        lifecycleScope.launch {
            val sesion = gestorSesion.obtenerSesion()
            if (sesion == null) {
                findNavController().navigate(R.id.accionCargaASeleccionUsuario)
            } else {
                when (sesion.rol.lowercase()) {
                    "comerciante" -> findNavController().navigate(R.id.accionCargaAInicioComerciante)
                    "administrador", "admin" -> findNavController().navigate(R.id.accionCargaAInicioAdministrador)
                    else -> findNavController().navigate(R.id.accionCargaASeleccionUsuario)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
