package io.github.stardomains3.oxproxion

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
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
        if (systemMessage == selectedMessage) {
            holder.title.setTextColor(ContextCompat.getColor(context, R.color.your_orange_color))
            holder.prompt.setTextColor(ContextCompat.getColor(context, R.color.your_orange_color))
        } else {
            holder.title.setTextColor(ContextCompat.getColor(context, R.color.white)) // Assuming default is white
            holder.prompt.setTextColor(ContextCompat.getColor(context, R.color.white)) // Assuming default is white
        }
        holder.itemView.setBackgroundColor(Color.TRANSPARENT)


        holder.itemView.setOnClickListener {
            onItemClick(systemMessage)
            selectedMessage = systemMessage
            notifyDataSetChanged()
        }

        holder.itemView.setOnLongClickListener {
            if (systemMessage.isDefault) {
                Toast.makeText(context, "Defaults can't be edited", Toast.LENGTH_SHORT).show()
            } else {
                onMenuClick(holder.itemView, systemMessage)
            }
            true
        }
    }

    override fun getItemCount() = systemMessages.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.system_message_title)
        val prompt: TextView = view.findViewById(R.id.system_message_prompt)
    }
}
