package io.github.stardomains3.oxproxion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class SavedChatsAdapter(
    private val onClick: (ChatSession) -> Unit,
    private val onLongClick: (ChatSession, View) -> Unit
) : ListAdapter<ChatSession, SavedChatsAdapter.ChatSessionViewHolder>(ChatSessionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatSessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_chat, parent, false)
        return ChatSessionViewHolder(view, onClick, onLongClick)
    }

    override fun onBindViewHolder(holder: ChatSessionViewHolder, position: Int) {
        val session = getItem(position)
        holder.bind(session)
    }

    class ChatSessionViewHolder(
        itemView: View,
        val onClick: (ChatSession) -> Unit,
        val onLongClick: (ChatSession, View) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.savedChatTitle)
        private val timestampTextView: TextView = itemView.findViewById(R.id.savedChatTimestamp)
        private val editIcon: ImageView = itemView.findViewById(R.id.iconEditt)
        private var currentSession: ChatSession? = null

        init {
            itemView.setOnClickListener {
                currentSession?.let {
                    onClick(it)
                }
            }
            /*itemView.setOnLongClickListener {
                currentSession?.let {
                    onLongClick(it, itemView)
                }
                true
            }*/
            editIcon.setOnClickListener {
                currentSession?.let {
                    onLongClick(it, editIcon) // Pass the icon as anchor view
                }
            }
        }

        fun bind(session: ChatSession) {
            currentSession = session
            titleTextView.text = session.title
            timestampTextView.text = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())
                .format(Date(session.timestamp))
        }
    }
}

class ChatSessionDiffCallback : DiffUtil.ItemCallback<ChatSession>() {
    override fun areItemsTheSame(oldItem: ChatSession, newItem: ChatSession): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ChatSession, newItem: ChatSession): Boolean {
        return oldItem == newItem
    }
}
