package com.feriapp.app.ui.consumidor

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.feriapp.app.data.model.ProductoBuscado
import com.feriapp.app.databinding.ItemSugerenciaBinding

class AdaptadorSugerencias(
    private val alSeleccionar: (ProductoBuscado) -> Unit
) : RecyclerView.Adapter<AdaptadorSugerencias.ViewHolderSugerencia>() {

    private val listaSugerencias = mutableListOf<ProductoBuscado>()

    fun actualizarLista(nuevaLista: List<ProductoBuscado>) {
        listaSugerencias.clear()
        listaSugerencias.addAll(nuevaLista)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolderSugerencia {
        val binding = ItemSugerenciaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolderSugerencia(binding)
    }

    override fun onBindViewHolder(holder: ViewHolderSugerencia, position: Int) {
        holder.bind(listaSugerencias[position])
    }

    override fun getItemCount(): Int = listaSugerencias.size

    inner class ViewHolderSugerencia(
        private val binding: ItemSugerenciaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(producto: ProductoBuscado) {
            binding.tvNombreProducto.text = producto.nombreProducto
            binding.tvNombreCategoria.text = producto.nombreCategoria
            binding.root.setOnClickListener {
                alSeleccionar(producto)
            }
        }
    }
}
