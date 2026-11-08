package com.feriapp.app.ui.consumidor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.feriapp.app.databinding.FragmentoResultadosBinding

class FragmentoResultados : Fragment() {

    private var _binding: FragmentoResultadosBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentoResultadosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val nombre = arguments?.getString("nombre")
        val idCategoria = arguments?.getInt("idCategoria", -1) ?: -1

        binding.tvParametrosBusqueda.text = "Búsqueda: nombre=$nombre, idCategoria=$idCategoria"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
