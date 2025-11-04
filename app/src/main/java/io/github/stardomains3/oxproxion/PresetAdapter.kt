package io.github.stardomains3.oxproxion

import android.graphics.Color import android.view.LayoutInflater import android.view.View import android.view.ViewGroup import android.view.WindowManager import android.widget.ImageView import android.widget.PopupWindow import android.widget.TextView import androidx.core.graphics.drawable.toDrawable import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.isVisible

class PresetAdapter( private val onItemClicked: (Preset) -> Unit, private val onItemEdit: (Preset) -> Unit, private val onItemDelete: (Preset) -> Unit ) : RecyclerView.Adapter<PresetAdapter.PresetVH>() {

    private val items = mutableListOf<Preset>()

    fun update(list: List<Preset>) {
        items.clear()
        items.addAll(list)
        // Collapse everything on refresh
        items.forEach { it.isExpanded = false }
        notifyDataSetChanged()
    }

    fun move(from: Int, to: Int) {
        if (from == to) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    fun getItems(): List<Preset> = items

    class PresetVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.textPresetTitle)
        val subtitleContainer: ViewGroup = itemView.findViewById(R.id.containerPresetSubtitle) // new
        val subtitle: TextView = itemView.findViewById(R.id.textPresetSubtitle)
        val edit: ImageView = itemView.findViewById(R.id.iconEditPreset)
        val expandIcon: ImageView = itemView.findViewById(R.id.iconExpand) // new
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetVH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_preset, parent, false)
        return PresetVH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: PresetVH, position: Int) {
        val preset = items[position]
        holder.title.text = preset.title
        updateSubtitle(holder, preset)
        holder.subtitleContainer.visibility = if (preset.isExpanded) View.VISIBLE else View.GONE
        holder.expandIcon.rotation = if (preset.isExpanded) 180f else 0f

        // Expand/collapse on chevron click only
        holder.expandIcon.setOnClickListener {
            preset.isExpanded = !preset.isExpanded
            notifyItemChanged(position)
        }

        // Apply preset on list item click (root view)
        holder.itemView.setOnClickListener { onItemClicked(preset) }

        // Edit menu
        holder.edit.setOnClickListener {
            showPresetPopupWindow(holder.edit, preset)
        }
    }

    private fun updateSubtitle(holder: PresetVH, preset: Preset) {
        if (holder.subtitleContainer.isVisible) {
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

        val wm = context.getSystemService(WindowManager::class.java)
        val metrics = wm.maximumWindowMetrics
        val screenHeight = metrics.bounds.height()

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