package io.github.stardomains3.oxproxion

import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
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

class ChatAdapter(
    private val markwon: Markwon,
    private val viewModel: ChatViewModel,
    private val onSpeakText: (String, Int) -> Unit,
    private val onSynthesizeToWavFile: (String, Int) -> Unit,
    private val ttsAvailable: Boolean,
    private val onEditMessage: (Int, String) -> Unit,
    private val onRedoMessage: (Int, JsonElement) -> Unit,
    private val onDeleteMessage: (Int) -> Unit

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
        const val VIEW_TYPE_THINKING = 3 // For the "working..." message
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
                if (content == "working...") VIEW_TYPE_THINKING else VIEW_TYPE_ASSISTANT
            }
            else -> VIEW_TYPE_ASSISTANT // Default
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = inflater.inflate(R.layout.item_message_user, parent, false)
                UserViewHolder(view, markwon)
            }
            else -> { // Both AI and Thinking use the same base layout
                val view = inflater.inflate(R.layout.item_message_ai, parent, false)
                AssistantViewHolder(view, markwon, onSpeakText, onSynthesizeToWavFile)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        var contentText = getMessageText(message.content)

        when (holder) {
            is UserViewHolder -> holder.bind(message)
            is AssistantViewHolder -> holder.bind(message, position, isSpeaking, currentSpeakingPosition)
        }
        // Removed the redundant if statement
    }

    override fun getItemCount(): Int = messages.size

    // ViewHolder for User messages
    inner class UserViewHolder(itemView: View, private val markwon: Markwon) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
        private val copyButtonuser: ImageButton = itemView.findViewById(R.id.copyButtonuser)
        private val resendButton: ImageButton = itemView.findViewById(R.id.resendButton)
        private val editButton: ImageButton = itemView.findViewById(R.id.editButton)
        private val imageView: ImageView = itemView.findViewById(R.id.userImageView)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)


        fun bind(message: FlexibleMessage) {
            messageTextView.typeface = currentTypeface

            // UPDATED: Render with Markwon for Markdown support (code blocks, bold, etc.)
            val userContent = getMessageText(message.content)
            try {
                markwon.setMarkdown(messageTextView, userContent)
                messageTextView.movementMethod = LinkMovementMethod.getInstance()
            } catch (e: RuntimeException) {
                // NEW: Catch Prism4j-specific errors to prevent crash; fallback to plain text
                if (e.message?.contains("Prism4j") == true || e.message?.contains("entry nodes") == true) {
                    Toast.makeText(itemView.context, "Prism4j failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    messageTextView.text = userContent  // Plain text, no Markdown
                } else {
                    throw e  // Re-throw non-Prism4j errors
                }
            }
            val imageUriStr = message.imageUri
            if (!imageUriStr.isNullOrEmpty()) {
                try {
                    val userImageUri = imageUriStr.toUri()  // Parse string to Uri
                    // Preferred: Load from Uri (Coil)
                    val request = ImageRequest.Builder(itemView.context)
                        .data(userImageUri)
                        .target(imageView)
                        .build()
                    ImageLoader(itemView.context).enqueue(request)
                    imageView.visibility = View.VISIBLE
                    // Toast.makeText(itemView.context, "Flex!", Toast.LENGTH_SHORT).show()
                    // Tap: Open original
                    imageView.setOnClickListener {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(userImageUri, "image/*")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            itemView.context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(itemView.context, "Could not open image (may be deleted/moved)", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {

                    // Fallback: Base64 display (if available)
                    val base64 = getImageBase64(message.content)
                    if (base64 != null) {
                        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)  // Or rotated if needed
                        imageView.setImageBitmap(bitmap)
                        imageView.visibility = View.VISIBLE
                        // Optional: Add temp tap for base64 (as before)
                    } else {
                        imageView.visibility = View.GONE
                    }
                }
            } else {
                imageView.visibility = View.GONE
            }





            copyButtonuser.setOnClickListener {
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Copied Text", messageTextView.text.toString())
                clipboard.setPrimaryClip(clip)
                // Toast.makeText(itemView.context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            copyButtonuser.setOnLongClickListener {
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Copied Markdown", userContent)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(itemView.context, "Raw Markdown copied to clipboard", Toast.LENGTH_SHORT).show()
                true // Consume the long click
            }
            editButton.setOnClickListener {
                val messageText = messageTextView.text.toString()
                if (messageText.isNotBlank()) {
                    onEditMessage(bindingAdapterPosition, messageText)  // Invoke callback with position and text
                }
            }
            resendButton.setOnClickListener {
                onRedoMessage(bindingAdapterPosition, message.content)  // Invoke callback with position and original content
            }
            deleteButton.setOnClickListener {
                onDeleteMessage(bindingAdapterPosition)
            }

        }
    }

    // ViewHolder for AI and "Thinking" messages
    inner class AssistantViewHolder(
        itemView: View,
        private val markwon: Markwon,
        private val onSpeakText: (String, Int) -> Unit,
        private val onSynthesizeToWavFile: (String, Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
        private val copyButton: ImageButton = itemView.findViewById(R.id.copyButton)
        private val aipdfButton: ImageButton = itemView.findViewById(R.id.aipdfButton)
        private val shareButton: ImageButton = itemView.findViewById(R.id.shareButton)
        val ttsButton: ImageButton = itemView.findViewById(R.id.ttsButton)
        private val generatedImageView: ImageView = itemView.findViewById(R.id.generatedImageView)
        val messageContainer: ConstraintLayout = itemView.findViewById(R.id.messageContainer)
        private var pulseAnimator: ObjectAnimator? = null
        fun bind(message: FlexibleMessage, position: Int, isSpeaking: Boolean, currentPosition: Int) {
            messageTextView.typeface = currentTypeface
            val text = getMessageText(message.content)

            val reasoningText = message.reasoning?.let { "\n\n$it" } ?: ""
            val fullText = reasoningText + text  // Prepend reasoning for display

            try {
                markwon.setMarkdown(messageTextView, fullText)
                messageTextView.movementMethod = LinkMovementMethod.getInstance()
            } catch (e: RuntimeException) {
                // Catch Prism4j-specific errors to prevent crash; fallback to plain text
                if (e.message?.contains("Prism4j") == true || e.message?.contains("entry nodes") == true) {
                    Toast.makeText(itemView.context, "Assistant Prism4j failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    messageTextView.text = fullText  // Plain text, no Markdown
                } else {
                    throw e  // Re-throw non-Prism4j errors
                }
            }
            ttsButton.visibility = if (ttsAvailable) View.VISIBLE else View.GONE

            val isError = message.role == "assistant" && text.startsWith("**Error:**")  // Use original text
            val isThinking = text == "working..."  // Use original text
            if (isError) {
                messageContainer.setBackgroundResource(R.drawable.bg_error_message)
            } else {
                messageContainer.setBackgroundResource(R.drawable.bg_ai_message)
            }
            pulseAnimator?.cancel()          // 2. always stop old one
            pulseAnimator = null

            if (isThinking) {                // 3. start only when needed
                pulseAnimator = ObjectAnimator.ofFloat(messageContainer, "alpha", 0.2f, 1f).apply {
                    duration = 800
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                    start()
                }
            } else {
                messageContainer.alpha = 1f
            }

            val generatedUriStr = message.imageUri  // From FlexibleMessage
            if (!generatedUriStr.isNullOrEmpty()) {
                try {
                    val generatedUri = generatedUriStr.toUri()  // Parse string to Uri
                    // Load from Uri (Coil)
                    val request = ImageRequest.Builder(itemView.context)
                        .data(generatedUri)
                        .target(generatedImageView)
                        .build()
                    ImageLoader(itemView.context).enqueue(request)
                    generatedImageView.visibility = View.VISIBLE

                    // Tap: Open original
                    generatedImageView.setOnClickListener {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(generatedUri, "image/*")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            itemView.context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(itemView.context, "Could not open image (may be deleted/moved)", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    //     Log.e("ChatAdapter", "Invalid Uri for generated image: $generatedUriStr", e)
                    generatedImageView.visibility = View.GONE  // Hide (no base64 fallback for generated)
                }
            } else {
                generatedImageView.visibility = View.GONE
            }



            val rawMarkdown = fullText  // Use fullText for copy/share

            copyButton.setOnClickListener {
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Copied Text", messageTextView.text.toString())
                clipboard.setPrimaryClip(clip)
            }
            copyButton.setOnLongClickListener {
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Copied Markdown", rawMarkdown)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(itemView.context, "Raw Markdown copied to clipboard", Toast.LENGTH_SHORT).show()
                true // Consume the long click
            }
            /*shareButton.setOnClickListener {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, rawMarkdown) // Share the raw markdown text
                    putExtra(Intent.EXTRA_SUBJECT, "AI Assistant Message") // Optional subject
                }

                    itemView.context.startActivity(Intent.createChooser(shareIntent, "Share message via"))

            }*/
            shareButton.setOnClickListener {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, messageTextView.text.toString())  // Use the same plain text as the copy button
                    putExtra(Intent.EXTRA_SUBJECT, "AI Assistant Message")  // Optional subject
                }
                itemView.context.startActivity(Intent.createChooser(shareIntent, "Share message via"))
            }
            // android.util.Log.d("TTS_DEBUG", "Binding position $position, isSpeaking: $isSpeaking, currentPosition: $currentPosition")
            // Update TTS button icon based on state
            val iconRes = if (isSpeaking && position == currentPosition) {
                R.drawable.ic_stop_circle  // Your stop icon
            } else {
                R.drawable.ic_volume_up   // Your play/speaker icon
            }
            ttsButton.setImageResource(iconRes)
            //  android.util.Log.d("TTS_DEBUG", "Setting icon to $iconRes for position $position")
            ttsButton.setOnClickListener {

                val textToSpeak = messageTextView.text.toString()
                if (textToSpeak.isNotEmpty()) {
                    onSpeakText(textToSpeak, position)
                } else {
                    Toast.makeText(itemView.context, "No text to speak", Toast.LENGTH_SHORT).show()
                }
            }
            ttsButton.setOnLongClickListener {
                val textToSpeak = messageTextView.text.toString()
                if (textToSpeak.isNotEmpty()) {
                    onSynthesizeToWavFile(textToSpeak, position)
                } else {
                    Toast.makeText(itemView.context, "No text to save", Toast.LENGTH_SHORT).show()
                }
                true // Consume the long click
            }
            // Long-press to copy raw markdown
            aipdfButton.setOnClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    val pdfUri = withContext(Dispatchers.IO) {
                        try {
                            val generator = PdfGenerator(itemView.context)
                            val imageUriStr = message.imageUri  // NEW: From FlexibleMessage
                            val imageUri = imageUriStr?.toUri()  // Parse string to Uri
                            if (imageUri != null) {
                                // Case #2: Has generated image (with or without text)
                                generator.generateMarkdownPdfWithImage(rawMarkdown, imageUri.toString())
                            } else {
                                // Case #1: Text-only
                                generator.generateMarkdownPdf(rawMarkdown)
                            }
                        } catch (e: Exception) {
                            //  Log.e("ChatAdapter", "PDF generation failed", e)
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


            /*copyButton.setOnLongClickListener {
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
            }*/
        }
        internal fun stopPulse() {          // <-- only visible inside the adapter
            pulseAnimator?.cancel()
            pulseAnimator = null
            messageContainer.alpha = 1f
            messageContainer.clearAnimation()
            messageContainer.setBackgroundResource(R.drawable.bg_ai_message)
        }
    }
    private var currentTypeface: Typeface = Typeface.DEFAULT
    fun updateFont(newTypeface: Typeface?) {
        currentTypeface = newTypeface ?: Typeface.DEFAULT
        notifyDataSetChanged()
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is AssistantViewHolder) holder.stopPulse()
    }

}
