package io.github.stardomains3.oxproxion


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class SystemMessageAdapter(
    private val systemMessages: MutableList<SystemMessage>,
    var selectedMessage: SystemMessage,
    private val onItemClick: (SystemMessage) -> Unit,
    private val onMenuClick: (View, SystemMessage) -> Unit
) : RecyclerView.Adapter<SystemMessageAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_system_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val systemMessage = systemMessages[position]
        holder.title.text = systemMessage.title
        holder.prompt.text = systemMessage.prompt

        val context = holder.itemView.context
        if (systemMessage.title == selectedMessage.title && systemMessage.prompt == selectedMessage.prompt) {
            holder.title.setTextColor(ContextCompat.getColor(context, R.color.ora))
            holder.prompt.setTextColor(ContextCompat.getColor(context, R.color.white))
        } else {
            holder.title.setTextColor(ContextCompat.getColor(context, R.color.white))
            holder.prompt.setTextColor(ContextCompat.getColor(context, R.color.white))
        }


        // Bind expand/collapse state
        holder.prompt.visibility = if (systemMessage.isExpanded) View.VISIBLE else View.GONE
        holder.expandIcon.rotation = if (systemMessage.isExpanded) 180f else 0f

        // Make title and chevron clickable/focusable
        holder.title.isClickable = true
        holder.title.isFocusable = true
        holder.expandIcon.isClickable = true
        holder.expandIcon.isFocusable = true

        // Chevron click: Toggle expand/collapse ONLY (no selection/pop)
        holder.expandIcon.setOnClickListener {
            systemMessage.isExpanded = !systemMessage.isExpanded
            notifyItemChanged(position)
        }

        // Title click: Auto-expand if compact + select (existing flow)
        holder.title.setOnClickListener {
            /* if (!systemMessage.isExpanded) {
                 systemMessage.isExpanded = true
                 holder.prompt.visibility = View.VISIBLE
                 holder.expandIcon.rotation = 180f
             }*/
            onItemClick(systemMessage)
            selectedMessage = systemMessage
            notifyDataSetChanged()
        }

        // Prompt click (when expanded): Select (consistent)
        holder.prompt.setOnClickListener {
            onItemClick(systemMessage)
            selectedMessage = systemMessage
            notifyDataSetChanged()
        }

        // Menu click (unchanged)
        holder.menuButton.setOnClickListener {
            onMenuClick(holder.menuButton, systemMessage)
        }

        // Full item fallback: For card bg/empty space (same as title)
        holder.itemView.setOnClickListener {
            /*if (!systemMessage.isExpanded) {
                systemMessage.isExpanded = true
                holder.prompt.visibility = View.VISIBLE
                holder.expandIcon.rotation = 180f
            }*/
            onItemClick(systemMessage)
            selectedMessage = systemMessage
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = systemMessages.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.system_message_title)
        val prompt: TextView = view.findViewById(R.id.system_message_prompt)
        val menuButton: ImageView = view.findViewById(R.id.menu_button)
        val expandIcon: ImageView = view.findViewById(R.id.expand_icon)
    }
}
