package io.github.stardomains3.oxproxion

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ChatAdapter(
    private val markwon: Markwon
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private fun getMessageText(content: JsonElement): String {
        if (content is JsonPrimitive) return content.content
        if (content is JsonArray) {
            return content.firstNotNullOfOrNull { item ->
                (item as? JsonObject)?.takeIf { it["type"]?.jsonPrimitive?.contentOrNull == "text" }?.get("text")?.jsonPrimitive?.content
            } ?: ""
        }
        return ""
    }

    private fun getImageBase64(content: JsonElement): String? {
        if (content is JsonArray) {
            return content.firstNotNullOfOrNull { item ->
                (item as? JsonObject)?.takeIf { it["type"]?.jsonPrimitive?.contentOrNull == "image_url" }?.get("image_url")?.jsonObject?.get("url")?.jsonPrimitive?.content?.substringAfter(",")
            }
        }
        return null
    }

    private val messages = mutableListOf<FlexibleMessage>()

    // Define constants for the view types
    private companion object {
        const val VIEW_TYPE_USER = 1
        const val VIEW_TYPE_ASSISTANT = 2
        const val VIEW_TYPE_THINKING = 3 // For the "thinking..." message
    }

    fun setMessages(newMessages: List<FlexibleMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(message: FlexibleMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun removeLastMessage() {
        if (messages.isNotEmpty()) {
            val lastIndex = messages.size - 1
            messages.removeAt(lastIndex)
            notifyItemRemoved(lastIndex)
        }
    }

    fun updateLastMessage(newMessage: FlexibleMessage) {
        if (messages.isNotEmpty()) {
            messages[messages.size - 1] = newMessage
            notifyItemChanged(messages.size - 1)
        }
    }


    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when (message.role) {
            "user" -> VIEW_TYPE_USER
            "assistant" -> {
                // Check if it's the special "thinking" message
                val content = (message.content as? JsonPrimitive)?.content ?: ""
                if (content == "thinking...") VIEW_TYPE_THINKING else VIEW_TYPE_ASSISTANT
            }
            else -> VIEW_TYPE_ASSISTANT // Default
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = inflater.inflate(R.layout.item_message_user, parent, false)
                UserViewHolder(view)
            }
            else -> { // Both AI and Thinking use the same base layout
                val view = inflater.inflate(R.layout.item_message_ai, parent, false)
                AssistantViewHolder(view, markwon)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val contentText = getMessageText(message.content)

        when (holder) {
            is UserViewHolder -> holder.bind(message)
            is AssistantViewHolder -> holder.bind(contentText)
        }
    }

    override fun getItemCount(): Int = messages.size

    // ViewHolder for User messages
    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
        private val copyButtonuser: ImageButton = itemView.findViewById(R.id.copyButtonuser)
        private val imageView: ImageView = itemView.findViewById(R.id.userImageView)
        fun bind(message: FlexibleMessage) {
            messageTextView.text = getMessageText(message.content)

            val base64 = getImageBase64(message.content)
            if (base64 != null) {
                val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                imageView.setImageBitmap(bitmap)
                imageView.visibility = View.VISIBLE
            } else {
                imageView.visibility = View.GONE
            }

            copyButtonuser.setOnClickListener {
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Copied Text", messageTextView.text.toString())
                clipboard.setPrimaryClip(clip)
                // Toast.makeText(itemView.context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ViewHolder for AI and "Thinking" messages
    inner class AssistantViewHolder(itemView: View, private val markwon: Markwon) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
        // Get a reference to the new button
        private val copyButton: ImageButton = itemView.findViewById(R.id.copyButton)

        fun bind(text: String) {
            markwon.setMarkdown(messageTextView, text)
            val rawMarkdown = text
            // Special handling for the copy button
            if (text == "thinking...") {
                // Hide the copy button when the AI is thinking
                //  copyButton.visibility = View.GONE
            } else {
                // Show it for actual responses and set its click listener
                //    copyButton.visibility = View.VISIBLE
                copyButton.setOnClickListener {
                    // Get the clipboard service
                    val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    // Get the raw text from the message TextView
                    val clip = ClipData.newPlainText("Copied Text", messageTextView.text.toString())
                    // Set the data to the clipboard
                    clipboard.setPrimaryClip(clip)

                    // Provide feedback to the user
                    //   Toast.makeText(itemView.context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
                copyButton.setOnLongClickListener {
                    val generator = PdfGenerator(itemView.context)
                    val pdfUri = generator.generateMarkdownPdf(rawMarkdown)
                    if (pdfUri != null) {
                        // Optional: Show toast or notification
                        Toast.makeText(itemView.context, "PDF saved to Downloads", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(itemView.context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
                    }
                    true // Consume the long click
                    //   val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    //   val clip = ClipData.newPlainText("Markdown", rawMarkdown)
                    //  clipboard.setPrimaryClip(clip)
                    // true // consume the long click
                }
            }
        }
    }
}