package io.github.stardomains3.oxproxion

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class OpenRouterModelsAdapter(
    private var models: List<LlmModel>,
    private val onItemClicked: (LlmModel) -> Unit
) : RecyclerView.Adapter<OpenRouterModelsAdapter.ModelViewHolder>() {

    fun updateModels(newModels: List<LlmModel>) {
        models = newModels
        notifyDataSetChanged()
    }

    class ModelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val modelId: TextView = view.findViewById(R.id.textModelName) // Reusing the same ID
        val modelName: TextView = view.findViewById(R.id.textModelDisplayName) // We'll need a new ID for this
        val modelIcon: ImageView = view.findViewById(R.id.iconModelType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        // We need a new layout item for this, let's call it list_item_open_router_model
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_open_router_model, parent, false)
        return ModelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        val model = models[position]
        holder.modelId.text = model.apiIdentifier
        holder.modelName.text = model.displayName

        val iconRes = if (model.isVisionCapable) {
            R.drawable.ic_vision
        } else {
            R.drawable.ic_person
        }
        holder.modelIcon.setImageResource(iconRes)

        holder.itemView.setOnClickListener {
            onItemClicked(model)
        }

        holder.itemView.setOnLongClickListener {
            val url = "https://openrouter.ai/${model.apiIdentifier}"
            val intent = Intent(Intent.ACTION_VIEW).setData(Uri.parse(url))
            try {
                holder.itemView.context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(holder.itemView.context, "Could not open browser.", Toast.LENGTH_SHORT).show()
            }
            true // Consume the long click
        }
    }

    override fun getItemCount() = models.size
}
