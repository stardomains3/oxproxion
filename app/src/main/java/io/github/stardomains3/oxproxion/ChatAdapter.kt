package io.github.stardomains3.oxproxion

import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.request.ImageRequest
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.collections.get

class ChatAdapter(
    private val markwon: Markwon,
    private val viewModel: ChatViewModel,
    private val onSpeakText: (String, Int) -> Unit,
    private val ttsAvailable: Boolean
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var isSpeaking = false
    var currentSpeakingPosition = -1
    var currentHolder: AssistantViewHolder? = null

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

    fun updateTtsState(speaking: Boolean, position: Int) {
        isSpeaking = speaking
        currentSpeakingPosition = position
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
                AssistantViewHolder(view, markwon, onSpeakText)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val contentText = getMessageText(message.content)

        when (holder) {
            is UserViewHolder -> holder.bind(message)
            is AssistantViewHolder -> holder.bind(message, position, isSpeaking, currentSpeakingPosition)
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
    inner class AssistantViewHolder(
        itemView: View,
        private val markwon: Markwon,
        private val onSpeakText: (String, Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
        private val shareButton: ImageButton = itemView.findViewById(R.id.shareButton)
        private val aipdfButton: ImageButton = itemView.findViewById(R.id.aipdfButton)
        private val copyButton: ImageButton = itemView.findViewById(R.id.copyButton)
         val ttsButton: ImageButton = itemView.findViewById(R.id.ttsButton)  // Added for TTS
        private val generatedImageView: ImageView = itemView.findViewById(R.id.generatedImageView)
        val messageContainer: ConstraintLayout = itemView.findViewById(R.id.messageContainer)

        fun bind(message: FlexibleMessage, position: Int, isSpeaking: Boolean, currentPosition: Int) {
            currentHolder = this@AssistantViewHolder
            if (position == currentPosition) {
                currentHolder = this
            }
            val text = getMessageText(message.content)
            markwon.setMarkdown(messageTextView, text)
            messageTextView.movementMethod = LinkMovementMethod.getInstance()
            ttsButton.visibility = if (ttsAvailable) View.VISIBLE else View.GONE
            val isThinking = text == "thinking..."
            val isError = message.role == "assistant" && text.startsWith("**Error:**")
            if (isError) {
                messageContainer.setBackgroundResource(R.drawable.bg_error_message)
            } else {
                messageContainer.setBackgroundResource(R.drawable.bg_ai_message)
            }
            if (isThinking) {
                val pulse = ObjectAnimator.ofFloat(messageContainer, "alpha", 0.2f, 1f).apply {
                    duration = 800
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                }
                pulse.start()
            } else {
                messageContainer.alpha = 1f // Reset alpha when not thinking
                messageContainer.clearAnimation() // Stop any ongoing animations
            }
            val imageUri = viewModel.generatedImages[position]  // Access temp map from ViewModel
            if (imageUri != null) {
                generatedImageView.visibility = View.VISIBLE
                val request = ImageRequest.Builder(itemView.context)
                    .data(imageUri)
                    .target(generatedImageView)
                    .build()
                ImageLoader(itemView.context).enqueue(request)  // Use Coil to load
                generatedImageView.setOnClickListener {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(imageUri.toUri(), "image/*")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        itemView.context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(itemView.context, "Could not open image", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                generatedImageView.visibility = View.GONE
            }
            val rawMarkdown = text

            // Update TTS button icon based on state
            val iconRes = if (isSpeaking && position == currentPosition) {
                R.drawable.ic_stop_circle  // Your stop icon
            } else {
                R.drawable.ic_volume_up  // Your play/speaker icon
            }
            ttsButton.setImageResource(iconRes)
            ttsButton.setOnClickListener {
                val textToSpeak = messageTextView.text.toString()
                if (textToSpeak.isNotEmpty()) {
                    onSpeakText(textToSpeak, position)
                } else {
                    Toast.makeText(itemView.context, "No text to speak", Toast.LENGTH_SHORT).show()
                }
            }
            aipdfButton.setOnClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    val pdfUri = withContext(Dispatchers.IO) {
                        try {
                            val generator = PdfGenerator(itemView.context)
                            val imageUri = viewModel.generatedImages[position]  // Check for generated image
                            if (imageUri != null) {
                                // Case #2: Has generated image (with or without text)
                                generator.generateMarkdownPdfWithImage(rawMarkdown, imageUri)
                            } else {
                                // Case #1: Text-only
                                generator.generateMarkdownPdf(rawMarkdown)
                            }
                        } catch (e: Exception) {
                            // Log.e("ChatAdapter", "PDF generation failed", e)
                            null
                        }
                    }

                    if (pdfUri != null) {
                        Toast.makeText(itemView.context, "PDF saved to Downloads", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(itemView.context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
                    }
                }
            }
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
                    // Launch on main thread (UI), but do the work on IO
                    CoroutineScope(Dispatchers.Main).launch {
                        val pdfUri = withContext(Dispatchers.IO) {
                            try {
                                val generator = PdfGenerator(itemView.context)
                                generator.generateMarkdownPdf(rawMarkdown)
                            } catch (e: Exception) {
                                //Log.e("ChatAdapter", "PDF generation failed", e)
                                null
                            }
                        }

                        if (pdfUri != null) {
                            Toast.makeText(itemView.context, "PDF saved to Downloads", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(itemView.context, "Failed to save PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true // Consume the long click
                }
                shareButton.setOnClickListener {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, messageTextView.text.toString())  // Use the same plain text as the copy button
                        putExtra(Intent.EXTRA_SUBJECT, "AI Assistant Message")  // Optional subject
                    }
                    itemView.context.startActivity(Intent.createChooser(shareIntent, "Share message via"))
                }
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is AssistantViewHolder) {
            holder.messageContainer.clearAnimation()  // Stop any running animations
            holder.messageContainer.alpha = 1f        // Reset transparency to normal
            // You might also want to reset the background here if needed:
            holder.messageContainer.setBackgroundResource(R.drawable.bg_ai_message)
        }
    }
}
