package com.feriapp.app.ui.consumidor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.feriapp.app.R
import com.feriapp.app.databinding.FragmentoBusquedaBinding

class FragmentoBusqueda : Fragment() {

    private var _binding: FragmentoBusquedaBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentoBusquedaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnVerResultados.setOnClickListener {
            val paqueteArgs = bundleOf(
                "nombre" to "Frutas",
                "idCategoria" to 1,
            )
            findNavController().navigate(R.id.accionBusquedaAResultados, paqueteArgs)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
