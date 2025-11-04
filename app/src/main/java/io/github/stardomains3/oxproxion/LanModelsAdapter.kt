package io.github.stardomains3.oxproxion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LanModelsAdapter(
    private var models: List<LlmModel>,
    private val onItemClicked: (LlmModel) -> Unit
) : RecyclerView.Adapter<LanModelsAdapter.ModelViewHolder>() {

    fun updateModels(newModels: List<LlmModel>) {
        models = newModels
        notifyDataSetChanged()
    }

    class ModelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val modelId: TextView = view.findViewById(R.id.textModelName)
        val modelName: TextView = view.findViewById(R.id.textModelDisplayName)
        val modelIcon: ImageView = view.findViewById(R.id.iconModelType)
        // No right icon for LAN models
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_lan_model, parent, false)
        return ModelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        val model = models[position]
        holder.modelId.text = model.apiIdentifier
        holder.modelName.text = model.displayName

        // All LAN models use the LAN icon
        holder.modelIcon.setImageResource(R.drawable.ic_lan)

        holder.itemView.setOnClickListener {
            onItemClicked(model)
        }
        // No long press functionality for LAN models
    }

    override fun getItemCount() = models.size
}
