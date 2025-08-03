package io.github.stardomains3.oxproxion


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class BotModelAdapter(
    private var models: MutableList<LlmModel>,
    private var currentModelId: String?,
    private val onItemClicked: (LlmModel) -> Unit,
    private val onItemEdit: (LlmModel) -> Unit,
    private val onItemDelete: (LlmModel) -> Unit
) : RecyclerView.Adapter<BotModelAdapter.ModelViewHolder>() {

    fun updateCurrentModel(newModelId: String?) {
        currentModelId = newModelId
    }

    class ModelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val modelName: TextView = view.findViewById(R.id.textModelName)
        val modelIcon: ImageView = view.findViewById(R.id.iconModelType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_model, parent, false)
        return ModelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        val model = models[position]
        holder.modelName.text = model.displayName
        if (model.apiIdentifier == currentModelId) {
            // Use a color from your res/values/colors.xml
            val orangeColor = ContextCompat.getColor(holder.itemView.context, R.color.your_orange_color)
            holder.modelName.setTextColor(orangeColor)
        } else {
            // IMPORTANT: Reset to default color for other items
            val defaultColor = ContextCompat.getColor(holder.itemView.context, R.color.white)
            holder.modelName.setTextColor(defaultColor)
        }
        val iconRes = if (model.isVisionCapable) {
            R.drawable.ic_vision // Your vision-capable icon
        } else {
            R.drawable.ic_person   // Your text-only icon
        }
        holder.modelIcon.setImageResource(iconRes)

        holder.itemView.setOnClickListener {
            onItemClicked(model)
        }

        holder.itemView.setOnLongClickListener {
            if (model.apiIdentifier == "meta-llama/llama-4-maverick") {
                android.widget.Toast.makeText(holder.itemView.context, "Maverick is the permanent default model and cannot be edited or deleted.", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                val popup = android.widget.PopupMenu(holder.itemView.context, holder.itemView)
                popup.menuInflater.inflate(R.menu.model_item_menu, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.edit_model -> {
                            onItemEdit(model)
                            true
                        }
                        R.id.delete_model -> {
                            onItemDelete(model)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
            true
        }
    }

    override fun getItemCount() = models.size
}