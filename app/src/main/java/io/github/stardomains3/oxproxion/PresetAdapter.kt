package io.github.stardomains3.oxproxion

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.RecyclerView

class PresetAdapter(
    private val onItemClicked: (Preset) -> Unit,
    private val onItemEdit: (Preset) -> Unit,
    private val onItemDelete: (Preset) -> Unit
) : RecyclerView.Adapter<PresetAdapter.PresetVH>() {

    private val items = mutableListOf<Preset>()

    fun update(list: List<Preset>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class PresetVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.textPresetTitle)
        val subtitle: TextView = itemView.findViewById(R.id.textPresetSubtitle)
        val edit: ImageView = itemView.findViewById(R.id.iconEditPreset)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetVH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_preset, parent, false)
        return PresetVH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: PresetVH, position: Int) {
        val preset = items[position]
        holder.title.text = preset.title
        holder.subtitle.text = buildString {
            append("Model: ")
            append(preset.modelIdentifier)
            append(" • ")
            append("SysMsg: ")
            append(preset.systemMessage.title)
            append(" • Stream: ")
            append(if (preset.streaming) "On" else "Off")
            append(" • Reason: ")
            append(if (preset.reasoning) "On" else "Off")
            append(" • Convo: ")
            append(if (preset.conversationMode) "On" else "Off")
        }

        holder.itemView.setOnClickListener { onItemClicked(preset) }
        holder.edit.setOnClickListener {
            showPresetPopupWindow(holder.edit, preset)  // Anchored dimmed popup near icon
        }
    }
    private fun showPresetPopupWindow(anchorView: View, preset: Preset) {
        val inflater = LayoutInflater.from(anchorView.context)
        val menuView = inflater.inflate(R.layout.menu_popup_layout, null)

        val popupWindow = PopupWindow(
            menuView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        popupWindow.isOutsideTouchable = true
        val context = anchorView.context
        val rootView = (context as android.app.Activity).window.decorView.findViewById<ViewGroup>(android.R.id.content)
        val dimView = View(context).apply {
            setBackgroundColor(Color.argb(204, 0, 0, 0)) // ~60% opacity dim
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        rootView.addView(dimView)

        popupWindow.setOnDismissListener {
            rootView.removeView(dimView)
        }
        val editItem = menuView.findViewById<TextView>(R.id.menu_edit)
        val deleteItem = menuView.findViewById<TextView>(R.id.menu_delete)

        editItem.setOnClickListener {
            popupWindow.dismiss()
            onItemEdit(preset)  // Invokes your existing full-screen edit
        }

        deleteItem.setOnClickListener {
            popupWindow.dismiss()
            onItemDelete(preset)  // Invokes your existing delete
        }

        // Smart positioning (below or above icon based on space):
        menuView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupHeight = menuView.measuredHeight

        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val anchorY = location[1]
        val anchorHeight = anchorView.height

        // UPDATED: Modern API for screen height (no deprecations on API 30+)
        val screenHeight: Int = {
            val wm = context.getSystemService(WindowManager::class.java)
            val metrics = wm.maximumWindowMetrics
            metrics.bounds.height()
        }()


        val spaceBelow = screenHeight - anchorY - anchorHeight
        val spaceAbove = anchorY

        val showAbove = spaceBelow < popupHeight && spaceAbove >= popupHeight

        if (showAbove) {
            popupWindow.showAsDropDown(anchorView, 0, -anchorHeight - popupHeight)
        } else {
            popupWindow.showAsDropDown(anchorView)
        }
    }


}