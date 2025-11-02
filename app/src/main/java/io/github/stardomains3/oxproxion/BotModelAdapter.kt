package io.github.stardomains3.oxproxion


import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
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
        val editIcon: ImageView = view.findViewById(R.id.iconEdit)
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
            val orangeColor = ContextCompat.getColor(holder.itemView.context, R.color.ora)
            holder.modelName.setTextColor(orangeColor)
        } else {
            // IMPORTANT: Reset to default color for other items
            val defaultColor = ContextCompat.getColor(holder.itemView.context, R.color.white)
            holder.modelName.setTextColor(defaultColor)
        }
        val iconRes = when {
            model.isImageGenerationCapable -> R.drawable.ic_palette  // NEW: Image generation models
            model.isVisionCapable -> R.drawable.ic_vision           // Vision models (image input)
            else -> R.drawable.ic_person                            // Text-only models
        }
        holder.modelIcon.setImageResource(iconRes)

        holder.itemView.setOnClickListener {
            onItemClicked(model)
        }
        holder.itemView.setOnLongClickListener {
            val apiIdentifier = model.apiIdentifier
            if (apiIdentifier.startsWith("@preset")) {
                Toast.makeText(holder.itemView.context, "Presets don't have a web page", Toast.LENGTH_SHORT).show()
            } else {
                val url = "https://openrouter.ai/$apiIdentifier"
                val intent = Intent(Intent.ACTION_VIEW).setData(url.toUri())
                try {
                    holder.itemView.context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(holder.itemView.context, "Could not open browser.", Toast.LENGTH_SHORT).show()
                }
            }
            true // Consume the long click
        }

        // ONLY ONE LongClickListener - the PopupWindow version
        /*holder.itemView.setOnLongClickListener {
            if (model.apiIdentifier == "meta-llama/llama-4-maverick") {
                android.widget.Toast.makeText(holder.itemView.context, "Maverick is the permanent default model and cannot be edited or deleted.", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                showModelPopupWindow(holder.itemView, model)
            }
            true
        }*/
        holder.editIcon.setOnClickListener {
            if (model.apiIdentifier == "meta-llama/llama-4-maverick") {
                Toast.makeText(holder.itemView.context, "Maverick is the permanent default model and cannot be edited or deleted.", Toast.LENGTH_SHORT).show()
            } else {
                showModelPopupWindow(holder.editIcon, model) // Pass the icon as anchor
            }
        }
    }

    override fun getItemCount() = models.size

    // Add the PopupWindow helper function here
    private fun showModelPopupWindow(anchorView: View, model: LlmModel) {
        val inflater = LayoutInflater.from(anchorView.context)
        val menuView = inflater.inflate(R.layout.menu_popup_layout, null)

        val popupWindow = PopupWindow(
            menuView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // Setup background - Important for dismissing when touching outside
        popupWindow.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        popupWindow.isOutsideTouchable = true
        val context = anchorView.context
        val rootView = (context as android.app.Activity).window.decorView.findViewById<ViewGroup>(android.R.id.content)
        val dimView = View(context).apply {
            setBackgroundColor(Color.argb(204, 0, 0, 0)) // 150 = ~60% opacity
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        rootView.addView(dimView)

        // Remove dim when popup is dismissed
        popupWindow.setOnDismissListener {
            rootView.removeView(dimView)
        }
        val editItem = menuView.findViewById<TextView>(R.id.menu_edit)
        val deleteItem = menuView.findViewById<TextView>(R.id.menu_delete)

        editItem.setOnClickListener {
            popupWindow.dismiss()
            onItemEdit(model)
        }

        deleteItem.setOnClickListener {
            popupWindow.dismiss()
            onItemDelete(model)
        }

        // --- SMART POSITIONING LOGIC ---

        // Measure the popup content to get its height
        menuView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupHeight = menuView.measuredHeight

        // Get the location of the anchor view on screen
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val anchorY = location[1]
        val anchorHeight = anchorView.height

        // Get screen height (modern API for API 31+)
        val wm = context.getSystemService(WindowManager::class.java)
        val screenHeight = wm.maximumWindowMetrics.bounds.height()

        // Calculate available space below and above the anchor
        val spaceBelow = screenHeight - anchorY - anchorHeight
        val spaceAbove = anchorY

        // Decide whether to show above or below based on available space
        val showAbove = spaceBelow < popupHeight && spaceAbove >= popupHeight

        // Show the popup in the correct position
        if (showAbove) {
            // Show above the anchor view
            popupWindow.showAsDropDown(anchorView, 0, -anchorHeight - popupHeight)
        } else {
            // Show below the anchor view (default behavior)
            popupWindow.showAsDropDown(anchorView)
        }
    }
    // In BotModelAdapter.kt, add this method inside the class:
    fun updateModels(newModels: MutableList<LlmModel>) {
        models = newModels
        notifyDataSetChanged()
    }

}
