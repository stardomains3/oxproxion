package io.github.stardomains3.oxproxion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView

class LanModelsAdapter(
    private var models: List<LlmModel>,
    private val isLlamaCppProvider: Boolean,
    private val onItemClicked: (LlmModel) -> Unit,
    private val onEjectClicked: ((LlmModel) -> Unit)? = null,
    private val onLoadClicked: ((LlmModel) -> Unit)? = null
) : RecyclerView.Adapter<LanModelsAdapter.ModelViewHolder>() {

    // Track models that are currently loading or unloading
    private val loadingModels = mutableSetOf<String>()

    fun updateModels(newModels: List<LlmModel>) {
        models = newModels
        notifyDataSetChanged()
    }

    // New function to toggle loading state
    fun setLoadingState(modelId: String, isLoading: Boolean) {
        if (isLoading) {
            loadingModels.add(modelId)
        } else {
            loadingModels.remove(modelId)
        }
        notifyDataSetChanged()
    }

    class ModelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val modelId: TextView = view.findViewById(R.id.textModelName)
        val modelName: TextView = view.findViewById(R.id.textModelDisplayName)
        val modelIcon: ImageView = view.findViewById(R.id.iconModelType)
        val ejectButton: ImageButton = view.findViewById(R.id.btnEject)
        val loadButton: ImageButton = view.findViewById(R.id.btnLoad)
        val progressBar: ProgressBar = view.findViewById(R.id.progressLoading) // New reference
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

        holder.modelIcon.setImageResource(R.drawable.ic_lan)

        // Check if this specific model is loading
        val isActivelyLoading = loadingModels.contains(model.apiIdentifier)

        // Show progress bar if loading; otherwise, hide it
        holder.progressBar.isVisible = isActivelyLoading

        // Hide both action buttons if it is actively loading
        holder.ejectButton.isVisible = isLlamaCppProvider && model.isLoaded && !isActivelyLoading && onEjectClicked != null
        holder.loadButton.isVisible = isLlamaCppProvider && !model.isLoaded && !isActivelyLoading && onLoadClicked != null

        holder.ejectButton.setOnClickListener {
            onEjectClicked?.invoke(model)
        }

        holder.loadButton.setOnClickListener {
            onLoadClicked?.invoke(model)
        }

        holder.itemView.setOnClickListener {
            onItemClicked(model)
        }
    }

    override fun getItemCount() = models.size
}
