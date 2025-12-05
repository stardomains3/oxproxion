package io.github.stardomains3.oxproxion

import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.request.ImageRequest
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    private val scope: CoroutineScope, // <--- 1. REQUIRED: Pass viewLifecycleOwner.lifecycleScope
    private val markwon: Markwon,
    // private val viewModel: ChatViewModel,
    private val onSpeakText: (String, Int) -> Unit,
    private val onSynthesizeToWavFile: (String, Int) -> Unit,
    private val ttsAvailable: Boolean,
    private val onEditMessage: (Int, String) -> Unit,
    private val onRedoMessage: (Int, JsonElement) -> Unit,
    private val onDeleteMessage: (Int) -> Unit,
    private val onSaveMarkdown: (Int, String) -> Unit,
    private val onCaptureItemToPng: (Int) -> Unit

) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var isSpeaking = false
    var currentSpeakingPosition = -1

    // 2. OPTIMIZATION: Conflated Channel for throttling updates
    private val updateChannel = Channel<FlexibleMessage>(Channel.CONFLATED)

    private val messages = mutableListOf<FlexibleMessage>()

    init {
        // 3. OPTIMIZATION: Consumer loop
        scope.launch(Dispatchers.Main) {
            for (newMessage in updateChannel) {
                if (messages.isNotEmpty()) {
                    messages[messages.size - 1] = newMessage
                    // Send "STREAMING" payload to update ONLY text (avoids full re-bind)
                    notifyItemChanged(messages.size - 1, "STREAMING")
                }
                // Throttle updates to ~20fps (50ms) to save CPU/GPU
                delay(50)
            }
        }
    }

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

    // Define constants for the view types
    companion object {
        const val VIEW_TYPE_USER = 1
        const val VIEW_TYPE_ASSISTANT = 2
        const val VIEW_TYPE_THINKING = 3 // For the "working..." message
    }

    fun setMessages(newMessages: List<FlexibleMessage>) {
        if (newMessages.isEmpty()) {
            messages.clear()
            notifyDataSetChanged()
            return
        }

        // PERFECT CASE: Only 1 new message added
        if (messages.size == newMessages.size - 1 &&
            messages == newMessages.dropLast(1)) {
            addMessage(newMessages.last())  // ← Incremental! No blink!
           // Log.d("ADAPTER", "→ HIT STREAMING (notifyItemChanged)")
            return
        }

        // STREAMING CASE: Same size, only last message content changed
        if (messages.size == newMessages.size &&
            messages.dropLast(1) == newMessages.dropLast(1)) {
            updateLastMessage(newMessages.last())
            return
        }

        // Fallback: Full refresh
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
      //  Log.d("ADAPTER", "→ fallback")
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

    // 4. OPTIMIZATION: Send to channel instead of direct UI update
    fun updateLastMessage(newMessage: FlexibleMessage) {
        updateChannel.trySend(newMessage)
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

    // 5. OPTIMIZATION: Intercept Payloads for partial updates
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            // If we are just streaming text, perform a lightweight update
            if (payloads.first() == "STREAMING" && holder is AssistantViewHolder) {
                holder.bindTextOnly(messages[position])
                return
            }
        }
        // Otherwise do the heavy lifting (images, animations, listeners)
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]

        when (holder) {
            is UserViewHolder -> holder.bind(message)
            is AssistantViewHolder -> holder.bind(message, position, isSpeaking, currentSpeakingPosition)
        }
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
            val rawUserContent = getMessageText(message.content)
            val userContent = ensureTableSpacing(rawUserContent)
            try {
                markwon.setMarkdown(messageTextView, userContent)
                //  messageTextView.movementMethod = LinkMovementMethod.getInstance()
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
        private val markdownButton: ImageButton = itemView.findViewById(R.id.markdownButton)
        private val pngButton: ImageButton = itemView.findViewById(R.id.pngButton)
        val ttsButton: ImageButton = itemView.findViewById(R.id.ttsButton)
        private val generatedImageView: ImageView = itemView.findViewById(R.id.generatedImageView)
        val messageContainer: ConstraintLayout = itemView.findViewById(R.id.messageContainer)
        private var pulseAnimator: ObjectAnimator? = null
        private var bgColorAnimator: ObjectAnimator? = null

        // 6. OPTIMIZATION: Lightweight bind for streaming
        fun bindTextOnly(message: FlexibleMessage) {

            val text = getMessageText(message.content)
            val reasoning = message.reasoning
            // Logic: Only add newlines if we actually have reasoning text
            val displayText = if (!reasoning.isNullOrBlank()) {
                "$reasoning\n\n$text"
            } else {
                text
            }
            messageTextView.text = displayText

            /*val text = getMessageText(message.content)
            val reasoningText = message.reasoning?.let { "\n\n$it" } ?: ""
            val rawText = reasoningText + text
            val fullText = ensureTableSpacing(rawText)

            try {
                markwon.setMarkdown(messageTextView, fullText)
            } catch (e: RuntimeException) {
                if (e.message?.contains("Prism4j") == true || e.message?.contains("entry nodes") == true) {
                    messageTextView.text = fullText
                }
            }*/
        }

        fun bind(message: FlexibleMessage, position: Int, isSpeaking: Boolean, currentPosition: Int) {
            messageTextView.typeface = currentTypeface
            val text = getMessageText(message.content)

            val reasoningText = message.reasoning?.let { "\n\n$it" } ?: ""
            val rawText = reasoningText + text
            val fullText = ensureTableSpacing(rawText)

            try {
                markwon.setMarkdown(messageTextView, fullText)
                // messageTextView.movementMethod = LinkMovementMethod.getInstance()
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
            bgColorAnimator?.cancel()
            pulseAnimator = null
            bgColorAnimator = null

            if (isThinking) {
                // CLONE your original drawable for animation (preserves corners!)
                val originalDrawable = ContextCompat.getDrawable(itemView.context, R.drawable.bg_ai_message) as GradientDrawable
                val animatedDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(0xFF2C2C2C.toInt())  // Match your original color
                    cornerRadius = 16f * itemView.resources.displayMetrics.density  // 16dp → pixels
                }

                messageContainer.background = animatedDrawable  // Use animated clone

                // 1. Gentle alpha breath
                val alphaAnimator = ObjectAnimator.ofFloat(messageContainer, "alpha", 0.2f, 1f).apply {
                    duration = 4000
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                }

                // 2. Subtle color pulse ON THE DRAWABLE (corners preserved!)
                val colorAnimator = ObjectAnimator.ofArgb(
                    animatedDrawable,  // Animate the clone directly!
                    "color",           // GradientDrawable's color property
                    0xFF222f3d.toInt(),  // Dark pastel blue
                    0xFF2C2C2C.toInt()   // Back to original
                ).apply {
                    duration = 2000
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                }

                alphaAnimator.start()
                colorAnimator.start()

                pulseAnimator = alphaAnimator
                bgColorAnimator = colorAnimator

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

            shareButton.setOnClickListener {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, messageTextView.text.toString())  // Use the same plain text as the copy button
                    putExtra(Intent.EXTRA_SUBJECT, "AI Assistant Message")  // Optional subject
                }
                itemView.context.startActivity(Intent.createChooser(shareIntent, "Share message via"))
            }
            shareButton.setOnLongClickListener {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, rawMarkdown)  // Share raw markdown
                    putExtra(Intent.EXTRA_SUBJECT, "AI Assistant Raw Markdown")
                }
                itemView.context.startActivity(Intent.createChooser(shareIntent, "Share raw markdown via"))
                Toast.makeText(itemView.context, "Sharing raw markdown", Toast.LENGTH_SHORT).show()
                true  // Consume the long click
            }

            // Update TTS button icon based on state
            val iconRes = if (isSpeaking && position == currentPosition) {
                R.drawable.ic_stop_circle  // Your stop icon
            } else {
                R.drawable.ic_volume_up   // Your play/speaker icon
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
            ttsButton.setOnLongClickListener {
                val textToSpeak = messageTextView.text.toString()
                if (textToSpeak.isNotEmpty()) {
                    onSynthesizeToWavFile(textToSpeak, position)
                } else {
                    Toast.makeText(itemView.context, "No text to save", Toast.LENGTH_SHORT).show()
                }
                true // Consume the long click
            }

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
            pngButton.setOnClickListener {
                onCaptureItemToPng(bindingAdapterPosition)
            }
            markdownButton.setOnLongClickListener {
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Copied Markdown", rawMarkdown)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(itemView.context, "Raw Markdown copied to clipboard", Toast.LENGTH_SHORT).show()
                true
            }
            markdownButton.setOnClickListener {
                onSaveMarkdown(bindingAdapterPosition, rawMarkdown)
            }
        }

        internal fun stopPulse() {
            pulseAnimator?.cancel()
            bgColorAnimator?.cancel()
            pulseAnimator = null
            bgColorAnimator = null
            messageContainer.alpha = 1f
            messageContainer.clearAnimation()
            // PERFECT RESET: Always restore ORIGINAL drawable
            messageContainer.background = ContextCompat.getDrawable(itemView.context, R.drawable.bg_ai_message)
        }
    }
    private var currentTypeface: Typeface = Typeface.DEFAULT

    fun updateFont(newTypeface: Typeface?) {
        currentTypeface = newTypeface ?: Typeface.DEFAULT
        notifyDataSetChanged()
    }
    fun finalizeStreaming() {
        scope.launch(Dispatchers.Main) {
            // Wait 100ms (longer than the 50ms streaming delay)
            // to ensure the streaming loop has finished flushing its queue.
            delay(100)

            if (messages.isNotEmpty()) {
                // Trigger Full Bind (Markdown + Tables)
                notifyItemChanged(messages.size - 1)
            }
        }
    }
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is AssistantViewHolder) holder.stopPulse()
    }
    private fun ensureTableSpacing(markdown: String): String {
        val pattern = Regex("([^\\n])\\n(\\s*\\|.*\\|\\n)(\\s*\\|[\\s:-]+\\|)")
        return markdown.replace(pattern, "$1\n\n$2$3")
    }
}