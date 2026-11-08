package com.feriapp.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.feriapp.app.R
import com.feriapp.app.databinding.FragmentoRegistroBinding

class FragmentoRegistro : Fragment() {

    private var _binding: FragmentoRegistroBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentoRegistroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRegistrarse.setOnClickListener {
            findNavController().navigate(R.id.accionRegistroAInicioComerciante)
        }

        binding.btnIrAInicioSesion.setOnClickListener {
            findNavController().navigate(R.id.accionRegistroAInicioSesion)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
