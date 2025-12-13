package io.github.stardomains3.oxproxion

import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import io.noties.markwon.utils.NoCopySpannableFactory
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
    private val scope: CoroutineScope,
    private val markwon: Markwon,
    private val onSpeakText: (String, Int) -> Unit,
    private val onSynthesizeToWavFile: (String, Int) -> Unit,
    private val ttsAvailable: Boolean,
    private val onEditMessage: (Int, String) -> Unit,
    private val onRedoMessage: (Int, JsonElement) -> Unit,
    private val onDeleteMessage: (Int) -> Unit,
    private val onSaveMarkdown: (Int, String) -> Unit,
    private val onCaptureItemToBitmap: (Int, String) -> Unit,
    private val onShowMarkdown: (String) -> Unit,
    private val onSaveText: (Int, String) -> Unit

) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // --- STATE & CACHE ---
    // Changed to Map to use stable keys (content hash) instead of unstable positions
    private val collapsedStates = mutableMapOf<String, Boolean>()
    // The "Baked" Cache for Markdown CharSequences
    private val renderCache = HashMap<FlexibleMessage, CharSequence>()

    private val noCopyFactory = NoCopySpannableFactory.getInstance()
    var isSpeaking = false
    var currentSpeakingPosition = -1
    private var currentTypeface: Typeface = Typeface.DEFAULT

    // OPTIMIZATION: Conflated Channel for throttling updates
    private val updateChannel = Channel<FlexibleMessage>(Channel.CONFLATED)
    private val messages = mutableListOf<FlexibleMessage>()

    init {
        // OPTIMIZATION: Consumer loop
        scope.launch(Dispatchers.Main) {
            for (newMessage in updateChannel) {
                if (messages.isNotEmpty()) {
                    messages[messages.size - 1] = newMessage
                    // Send "STREAMING" payload to update ONLY text (avoids full re-bind)
                    notifyItemChanged(messages.size - 1, "STREAMING")
                }
                // Throttle updates to ~20fps (50ms)
                delay(50)
            }
        }
    }

    // --- PUBLIC METHODS ---

    fun clearCache() {
        renderCache.clear()
        collapsedStates.clear()
    }

    fun updateTtsState(speaking: Boolean, position: Int) {
        isSpeaking = speaking
        currentSpeakingPosition = position
    }

    fun updateFont(newTypeface: Typeface?) {
        currentTypeface = newTypeface ?: Typeface.DEFAULT
        notifyDataSetChanged()
    }

    fun finalizeStreaming() {
        scope.launch(Dispatchers.Main) {
            delay(100)
            if (messages.isNotEmpty()) {
                val lastIndex = messages.size - 1
                val message = messages[lastIndex]

                // OPTIMIZATION:
                // 1. Force the heavy calculation (Regex + Markdown) NOW.
                // This populates the renderCache[message] with the fixed table spacing.
                getPreRenderedContent(message)

                // 2. Notify the view.
                // When onBindViewHolder runs, it will call getPreRenderedContent,
                // find the data we just cached, and skip the heavy work.
                notifyItemChanged(lastIndex)
            }
        }
    }

    fun setMessages(newMessages: List<FlexibleMessage>) {
        // Clear cache if loading a fresh list or switching chats
        if (newMessages.isEmpty() || (messages.isEmpty() && newMessages.isNotEmpty())) {
            renderCache.clear()
        }

        if (newMessages.isEmpty()) {
            messages.clear()
            notifyDataSetChanged()
            return
        }

        // PERFECT CASE: Only 1 new message added
        if (messages.size == newMessages.size - 1 &&
            messages == newMessages.dropLast(1)) {
            addMessage(newMessages.last())
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
            val oldMessage = messages.last()
            renderCache.remove(oldMessage) // Invalidate cache for the streaming message
        }
        updateChannel.trySend(newMessage)
    }

    // --- DATA HELPERS ---

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

    // --- OPTIMIZED BAKING FUNCTION ---
    private fun getPreRenderedContent(message: FlexibleMessage): CharSequence {
        // 1. Check Cache
        if (renderCache.containsKey(message)) {
            return renderCache[message]!!
        }

        // 2. Extract Text
        val text = getMessageText(message.content)
        val reasoningText = message.reasoning?.let { "\n\n$it" } ?: ""
        val rawText = reasoningText + text

        // 3. Run Regex (Expensive)
        val fullText = ensureTableSpacing(rawText)

        // 4. Render Markdown with Safety (Expensive)
        val renderedContent = try {
            markwon.toMarkdown(fullText)
        } catch (e: RuntimeException) {
            // 5. Prism4j Crash Handler
            if (e.message?.contains("Prism4j") == true || e.message?.contains("entry nodes") == true) {
                fullText // Fallback: Return the plain text
            } else {
                throw e
            }
        }

        // 6. Save to Cache
        renderCache[message] = renderedContent

        return renderedContent
    }

    private fun ensureTableSpacing(md: String): String {
        val pattern = Regex(
            """(^[\t >]*([-+*]|\d+\.)\s+(?:\\\$\\\[ ?[ xX]?\\]\\\s+)?[^\n]*)\n(?=\|)""",
            RegexOption.MULTILINE
        )
        return md.replace(pattern) { "${it.value}\n\n" }
    }

    // --- VIEW HOLDER LOGIC ---

    companion object {
        const val VIEW_TYPE_USER = 1
        const val VIEW_TYPE_ASSISTANT = 2
        const val VIEW_TYPE_THINKING = 3
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when (message.role) {
            "user" -> VIEW_TYPE_USER
            "assistant" -> {
                val content = (message.content as? JsonPrimitive)?.content ?: ""
                if (content == "working...") VIEW_TYPE_THINKING else VIEW_TYPE_ASSISTANT
            }
            else -> VIEW_TYPE_ASSISTANT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = inflater.inflate(R.layout.item_message_user, parent, false)
                view.findViewById<TextView>(R.id.messageTextView)
                    .setSpannableFactory(noCopyFactory)
                UserViewHolder(view, markwon)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_message_ai, parent, false)
                view.findViewById<TextView>(R.id.messageTextView)
                    .setSpannableFactory(noCopyFactory)
                AssistantViewHolder(view, markwon, onSpeakText, onSynthesizeToWavFile)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            if (payloads.first() == "STREAMING" && holder is AssistantViewHolder) {
                holder.bindTextOnly(messages[position])
                return
            }
        }
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

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is AssistantViewHolder) holder.stopPulse()
    }

    // --- VIEW HOLDERS ---

    inner class UserViewHolder(itemView: View, private val markwon: Markwon) : RecyclerView.ViewHolder(itemView) {
        private val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
        private val copyButtonuser: ImageButton = itemView.findViewById(R.id.copyButtonuser)
        private val resendButton: ImageButton = itemView.findViewById(R.id.resendButton)
        private val editButton: ImageButton = itemView.findViewById(R.id.editButton)
        private val imageView: ImageView = itemView.findViewById(R.id.userImageView)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        private val collapseToggleButton: ImageButton = itemView.findViewById(R.id.collapseToggleButton)

        fun bind(message: FlexibleMessage) {
            messageTextView.typeface = currentTypeface
            val rawUserContent = getMessageText(message.content)
            val pos = bindingAdapterPosition
            collapseToggleButton.visibility = View.GONE

            if (pos >= 0 && message.role == "user") {
                val displayMetrics = itemView.resources.displayMetrics
                val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
                val isTablet = screenWidthDp >= 600
                val MAX_CHARS_THRESHOLD = if (isTablet) 300 else 150
                val MAX_LINES_THRESHOLD = 3

                val rawLines = rawUserContent.lines().size
                val charLength = rawUserContent.length
                val isLongMessage = rawLines > MAX_LINES_THRESHOLD || charLength > MAX_CHARS_THRESHOLD

                if (isLongMessage) {
                    // Use stable key (content hash) instead of position
                    val msgKey = rawUserContent.hashCode().toString()
                    val isCollapsed = collapsedStates.getOrDefault(msgKey, true)

                    val displayContent = if (isCollapsed) {
                        if (charLength > MAX_CHARS_THRESHOLD) {
                            val cutOffIndex =
                                rawUserContent.take(MAX_CHARS_THRESHOLD).lastIndexOf(' ')
                            val safeIndex = if (cutOffIndex > 0) cutOffIndex else MAX_CHARS_THRESHOLD
                            rawUserContent.take(safeIndex) + "...(continued)"
                        } else {
                            rawUserContent.lines().take(MAX_LINES_THRESHOLD).joinToString("\n") + "\n\n**...(continued)**"
                        }
                    } else {
                        rawUserContent
                    }

                    try {
                        markwon.setMarkdown(messageTextView, displayContent)
                    } catch (e: RuntimeException) {
                        if (e.message?.contains("Prism4j") == true || e.message?.contains("entry nodes") == true) {
                            messageTextView.text = displayContent
                        } else {
                            throw e
                        }
                    }

                    collapseToggleButton.visibility = View.VISIBLE
                    collapseToggleButton.setImageResource(
                        if (isCollapsed) R.drawable.ic_expand_more else R.drawable.ic_expand_less2
                    )
                    collapseToggleButton.setOnClickListener {
                        collapsedStates[msgKey] = !isCollapsed
                        this@ChatAdapter.notifyItemChanged(pos)
                    }
                } else {
                    try {
                        markwon.setMarkdown(messageTextView, rawUserContent)
                    } catch (e: RuntimeException) {
                        if (e.message?.contains("Prism4j") == true || e.message?.contains("entry nodes") == true) {
                            messageTextView.text = rawUserContent
                        } else {
                            throw e
                        }
                    }
                }
            } else {
                try {
                    markwon.setMarkdown(messageTextView, rawUserContent)
                } catch (e: RuntimeException) {
                    if (e.message?.contains("Prism4j") == true || e.message?.contains("entry nodes") == true) {
                        messageTextView.text = rawUserContent
                    } else {
                        throw e
                    }
                }
            }

            // ... (Image and Button logic) ...
            val imageUriStr = message.imageUri
            if (!imageUriStr.isNullOrEmpty()) {
                try {
                    val userImageUri = imageUriStr.toUri()
                    val request = ImageRequest.Builder(itemView.context)
                        .data(userImageUri)
                        .target(imageView)
                        .build()
                    ImageLoader(itemView.context).enqueue(request)
                    imageView.visibility = View.VISIBLE
                    imageView.setOnClickListener {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(userImageUri, "image/*")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            itemView.context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(itemView.context, "Could not open image", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    val base64 = getImageBase64(message.content)
                    if (base64 != null) {
                        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
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
                val clip = ClipData.newPlainText("Copied Markdown", rawUserContent)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(itemView.context, "Raw Markdown copied to clipboard", Toast.LENGTH_SHORT).show()
                true
            }
            editButton.setOnClickListener {
                val messageText = messageTextView.text.toString()
                if (messageText.isNotBlank()) {
                    onEditMessage(bindingAdapterPosition, messageText)
                }
            }
            resendButton.setOnClickListener {
                onRedoMessage(bindingAdapterPosition, message.content)
            }
            deleteButton.setOnClickListener {
                onDeleteMessage(bindingAdapterPosition)
            }
        }
    }

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
        private val htmlButton: ImageButton = itemView.findViewById(R.id.htmlButton)
        private val collapseToggleButton: ImageButton = itemView.findViewById(R.id.collapseToggleButton)

        // Configuration for "Long Message" detection
        private val CHAR_THRESHOLD = 350

        fun bindTextOnly(message: FlexibleMessage) {
            val text = getMessageText(message.content)
            val reasoning = message.reasoning
            val displayText = if (!reasoning.isNullOrBlank()) {
                "$reasoning\n\n$text"
            } else {
                text
            }
            messageTextView.text = displayText
        }

        fun bind(message: FlexibleMessage, position: Int, isSpeaking: Boolean, currentPosition: Int) {
            messageTextView.typeface = currentTypeface

            // 1. DISPLAY TEXT (Optimized: Uses Cache)
            val finalContent = getPreRenderedContent(message)
            messageTextView.text = finalContent

            // 2. LOGIC TEXT (Fast extraction)
            val text = getMessageText(message.content)

            // --- NEW COLLAPSE LOGIC (INSTANT, NO POST DELAY) ---
            if (text.length > CHAR_THRESHOLD) {
                val msgKey = text.hashCode().toString()
                val isCollapsed = collapsedStates.getOrDefault(msgKey, false) // Default Expanded (false)

                applyCollapseState(isCollapsed)

                collapseToggleButton.visibility = View.VISIBLE
                collapseToggleButton.setImageResource(
                    if (isCollapsed) R.drawable.ic_expand_more else R.drawable.ic_expand_less2
                )

                collapseToggleButton.setOnClickListener {
                    val newState = !collapsedStates.getOrDefault(msgKey, false)
                    collapsedStates[msgKey] = newState

                    applyCollapseState(newState)
                    collapseToggleButton.setImageResource(
                        if (newState) R.drawable.ic_expand_more else R.drawable.ic_expand_less2
                    )
                }
            } else {
                messageTextView.maxLines = Int.MAX_VALUE
                messageTextView.ellipsize = null
                collapseToggleButton.visibility = View.GONE
                collapseToggleButton.setOnClickListener(null)
            }
            // ---------------------------------------------------

            val reasoningText = message.reasoning?.let { "\n\n$it" } ?: ""

            // 3. UI STATE LOGIC
            ttsButton.visibility = if (ttsAvailable) View.VISIBLE else View.GONE

            val isError = message.role == "assistant" && text.startsWith("**Error:**")
            val isThinking = text == "working..."

            if (isError) {
                messageContainer.setBackgroundResource(R.drawable.bg_error_message)
            } else {
                messageContainer.setBackgroundResource(R.drawable.bg_ai_message)
            }

            // 4. ANIMATIONS
            pulseAnimator?.cancel()
            bgColorAnimator?.cancel()
            pulseAnimator = null
            bgColorAnimator = null

            if (isThinking) {
                val originalDrawable = ContextCompat.getDrawable(itemView.context, R.drawable.bg_ai_message) as GradientDrawable
                val animatedDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(0xFF2C2C2C.toInt())
                    cornerRadius = 16f * itemView.resources.displayMetrics.density
                }

                messageContainer.background = animatedDrawable

                val alphaAnimator = ObjectAnimator.ofFloat(messageContainer, "alpha", 0.2f, 1f).apply {
                    duration = 4000
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.REVERSE
                }

                val colorAnimator = ObjectAnimator.ofArgb(
                    animatedDrawable,
                    "color",
                    0xFF222f3d.toInt(),
                    0xFF2C2C2C.toInt()
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

            // 5. IMAGE LOADING
            val generatedUriStr = message.imageUri
            if (!generatedUriStr.isNullOrEmpty()) {
                try {
                    val generatedUri = generatedUriStr.toUri()
                    val request = ImageRequest.Builder(itemView.context)
                        .data(generatedUri)
                        .target(generatedImageView)
                        .build()
                    ImageLoader(itemView.context).enqueue(request)
                    generatedImageView.visibility = View.VISIBLE

                    generatedImageView.setOnClickListener {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(generatedUri, "image/*")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            itemView.context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(itemView.context, "Could not open image", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    generatedImageView.visibility = View.GONE
                }
            } else {
                generatedImageView.visibility = View.GONE
            }

            // 6. BUTTON LISTENERS (Lazy Calculation)
            htmlButton.setOnClickListener {
                val fullRawMarkdown = ensureTableSpacing(reasoningText + text)
                if (fullRawMarkdown.isNotBlank()) {
                    onShowMarkdown.invoke(fullRawMarkdown)
                }
            }

            copyButton.setOnClickListener {
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Copied Text", messageTextView.text.toString())
                clipboard.setPrimaryClip(clip)
            }

            copyButton.setOnLongClickListener {
                val fullRawMarkdown = ensureTableSpacing(reasoningText + text)
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Copied Markdown", fullRawMarkdown)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(itemView.context, "Raw Markdown copied to clipboard", Toast.LENGTH_SHORT).show()
                true
            }

            shareButton.setOnClickListener {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, messageTextView.text.toString())
                    putExtra(Intent.EXTRA_SUBJECT, "AI Assistant Message")
                }
                itemView.context.startActivity(Intent.createChooser(shareIntent, "Share message via"))
            }

            shareButton.setOnLongClickListener {
                val fullRawMarkdown = ensureTableSpacing(reasoningText + text)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, fullRawMarkdown)
                    putExtra(Intent.EXTRA_SUBJECT, "AI Assistant Raw Markdown")
                }
                itemView.context.startActivity(Intent.createChooser(shareIntent, "Share raw markdown via"))
                Toast.makeText(itemView.context, "Sharing raw markdown", Toast.LENGTH_SHORT).show()
                true
            }

            val iconRes = if (isSpeaking && position == currentPosition) {
                R.drawable.ic_stop_circle
            } else {
                R.drawable.ic_volume_up
            }
            ttsButton.setImageResource(iconRes)

            ttsButton.setOnClickListener {
                val textToSpeak = messageTextView.text.toString()
                if (textToSpeak.isNotEmpty()) {
                    ForegroundService.stopTtsSpeaking()
                    onSpeakText(textToSpeak, position)
                } else {
                    Toast.makeText(itemView.context, "No text to speak", Toast.LENGTH_SHORT).show()
                }
            }

            ttsButton.setOnLongClickListener {
                val textToSpeak = messageTextView.text.toString()
                if (textToSpeak.isNotEmpty()) {
                    ForegroundService.stopTtsSpeaking()
                    onSynthesizeToWavFile(textToSpeak, position)
                } else {
                    Toast.makeText(itemView.context, "No text to save", Toast.LENGTH_SHORT).show()
                }
                true
            }

            aipdfButton.setOnClickListener {
                val fullRawMarkdown = ensureTableSpacing(reasoningText + text)
                CoroutineScope(Dispatchers.Main).launch {
                    val pdfUri = withContext(Dispatchers.IO) {
                        try {
                            val generator = PdfGenerator(itemView.context)
                            val imageUriStr = message.imageUri
                            val imageUri = imageUriStr?.toUri()
                            if (imageUri != null) {
                                generator.generateMarkdownPdfWithImage(fullRawMarkdown, imageUri.toString())
                            } else {
                                generator.generateMarkdownPdf(fullRawMarkdown)
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
                onCaptureItemToBitmap(bindingAdapterPosition, "png")
            }

            pngButton.setOnLongClickListener {
                onCaptureItemToBitmap(bindingAdapterPosition, "webp")
                true
            }

            aipdfButton.setOnLongClickListener {
                onCaptureItemToBitmap(bindingAdapterPosition, "jpg")
                true
            }

            markdownButton.setOnClickListener {
                val fullRawMarkdown = ensureTableSpacing(reasoningText + text)
                onSaveMarkdown(bindingAdapterPosition, fullRawMarkdown)
            }

            markdownButton.setOnLongClickListener {
                onSaveText(bindingAdapterPosition, messageTextView.text.toString())
                true
            }
        }

        internal fun stopPulse() {
            pulseAnimator?.cancel()
            bgColorAnimator?.cancel()
            pulseAnimator = null
            bgColorAnimator = null
            messageContainer.alpha = 1f
            messageContainer.clearAnimation()
            messageContainer.background = ContextCompat.getDrawable(itemView.context, R.drawable.bg_ai_message)
        }

        private fun applyCollapseState(isCollapsed: Boolean) {
            messageTextView.maxLines = if (isCollapsed) 4 else Int.MAX_VALUE
            messageTextView.ellipsize = if (isCollapsed) android.text.TextUtils.TruncateAt.END else null
        }
    }
}
