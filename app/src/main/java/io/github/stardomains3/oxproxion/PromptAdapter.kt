package io.github.stardomains3.oxproxion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PromptAdapter(
    private val prompts: MutableList<Prompt>,
    private val onItemClick: (Prompt) -> Unit,
    private val onMenuClick: (View, Prompt) -> Unit
) : RecyclerView.Adapter<PromptAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_prompt, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val prompt = prompts[position]
        holder.title.text = prompt.title
        holder.promptText.text = prompt.prompt

        holder.promptText.visibility = if (prompt.isExpanded) View.VISIBLE else View.GONE
        holder.expandIcon.rotation = if (prompt.isExpanded) 180f else 0f

        holder.expandIcon.setOnClickListener {
            prompt.isExpanded = !prompt.isExpanded
            notifyItemChanged(position)
        }

        holder.title.setOnClickListener { onItemClick(prompt) }
        holder.promptText.setOnClickListener { onItemClick(prompt) }
        holder.itemView.setOnClickListener { onItemClick(prompt) }

        holder.menuButton.setOnClickListener { onMenuClick(holder.menuButton, prompt) }
    }

    override fun getItemCount() = prompts.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.prompt_title)
        val promptText: TextView = view.findViewById(R.id.prompt_prompt)
        val menuButton: ImageView = view.findViewById(R.id.menu_button)
        val expandIcon: ImageView = view.findViewById(R.id.expand_icon)
    }
}