package io.github.stardomains3.oxproxion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PromptAdapter(
    private val prompts: MutableList<Prompt>,
    private val onItemClick: (Prompt) -> Unit,      // For content area click - send to chat
    private val onCopyClick: (Prompt) -> Unit,       // For copy button - copy to clipboard
    private val onMenuClick: (View, Prompt) -> Unit  // For menu button
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

        // Expand/collapse only when expand icon is clicked
        holder.expandIcon.setOnClickListener {
            prompt.isExpanded = !prompt.isExpanded
            notifyItemChanged(position)
        }

        // Content area click - sends to chat
        holder.contentArea.setOnClickListener { onItemClick(prompt) }

        // Copy button - copies to clipboard
        holder.copyButton.setOnClickListener { onCopyClick(prompt) }

        // Menu button - shows edit/delete menu
        holder.menuButton.setOnClickListener { onMenuClick(holder.menuButton, prompt) }
    }

    override fun getItemCount() = prompts.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val contentArea: LinearLayout = view.findViewById(R.id.content_area)
        val title: TextView = view.findViewById(R.id.prompt_title)
        val promptText: TextView = view.findViewById(R.id.prompt_prompt)
        val menuButton: ImageView = view.findViewById(R.id.menu_button)
        val copyButton: ImageView = view.findViewById(R.id.copy_button)
        val expandIcon: ImageView = view.findViewById(R.id.expand_icon)
    }
}
