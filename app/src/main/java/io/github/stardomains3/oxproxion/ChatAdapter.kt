package io.github.stardomains3.oxproxion

import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Environment
import android.os.StrictMode
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
import com.google.android.material.snackbar.Snackbar
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
import java.io.File

class ChatAdapter(
    private val scope: CoroutineScope, // <--- 1. REQUIRED: Pass viewLifecycleOwner.lifecycleScope here
    private val markwon: Markwon,
    private val onSpeakText: (String, Int) -> Unit,
    private val onSynthesizeToWavFile: (String, Int) -> Unit,
    private val ttsAvailable: Boolean,
    private val onEditMessage: (Int, String) -> Unit,
    private val onRedoMessage: (Int, JsonElement) -> Unit,
    private val onDeleteMessage: (Int) -> Unit,
    private val onEditAssistantMessage: (Int, String) -> Unit,
    private val onSaveMarkdown: (Int, String) -> Unit,
    private val onCaptureItemToBitmap: (Int, String) -> Unit,
    private val onShowMarkdown: (String) -> Unit,
    private val onSaveHtml: (String) -> Unit,
    private val onSaveText: (Int, String) -> Unit,
    private val onCollapse: () -> Unit,
    private val onSaveAsFile: (String) -> Unit



) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val collapsedStates = mutableMapOf<String, Boolean>()
    private val renderCache = HashMap<FlexibleMessage, CharSequence>()
    private val noCopyFactory = NoCopySpannableFactory.getInstance()
    var isSpeaking = false
    var currentSpeakingPosition = -1
    private var currentTypeface: Typeface = Typeface.DEFAULT
    private val messages = mutableListOf<FlexibleMessage>()
    private var isUserApplyingEdit: Boolean = false
    private var editTargetPosition: Int = -1
    private var currentFontScale: Int = 100

    // 2. OPTIMIZATION: Conflated Channel for throttling
    private val updateChannel = Channel<FlexibleMessage>(Channel.CONFLATED)

    init {
        // 3. OPTIMIZATION: Consumer loop
        scope.launch(Dispatchers.Main) {
            for (newMessage in updateChannel) {
                if (messages.isNotEmpty()) {
                    messages[messages.size - 1] = newMessage
                    // Send "STREAMING" payload to avoid full re-bind
                    notifyItemChanged(messages.size - 1, "STREAMING")
                }
                // Throttle updates to ~20fps
                delay(50)
            }
        }
    }

    companion object {
        const val VIEW_TYPE_USER = 1
        const val VIEW_TYPE_ASSISTANT = 2
        const val VIEW_TYPE_THINKING = 3
        const val VIEW_TYPE_HIDDEN = 4
    }

    // --- DATA HELPERS ---
    fun flagEditUpdate(position: Int) {
        isUserApplyingEdit = true
        editTargetPosition = position
    }
    fun updateFontSize(scalePercent: Int) {
        currentFontScale = scalePercent.coerceIn(50, 200) // clamp 50%-200%
        notifyDataSetChanged()
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
    private fun getPreRenderedContent(message: FlexibleMessage): CharSequence {
        // 1. Check Cache
        if (renderCache.containsKey(message)) {
            return renderCache[message]!!
        }

        // 2. Extract Text (JSON Logic)
        val text = if (message.role == "assistant" && message.toolCalls != null && getMessageText(message.content).isBlank()) {
            // Show a clean, formatted indicator of what tool was used
            "🔧 **Tool Used:** ${message.toolCalls.map { it.function.name }.distinct().joinToString()}"
        } else {
            getMessageText(message.content)
        }

        //  val reasoningText = message.reasoning?.let { "\n\n$it" } ?: ""
        // val rawText = reasoningText + text

        // 3. Run Regex (Expensive)
        // val fullText = ensureTableSpacing(rawText)
        //val fullText = rawText
        val fullText = text


        // 4. Render Markdown with Safety (Expensive)
        val renderedContent = try {
            // Note: Use toMarkdown() instead of setMarkdown() to get the CharSequence
            markwon.toMarkdown(fullText)
        } catch (e: RuntimeException) {
            // 5. Prism4j Crash Handler
            if (e.message?.contains("Prism4j") == true || e.message?.contains("entry nodes") == true) {
                // Fallback: Return the plain text so the app doesn't crash
                fullText
            } else {
                // If it's a different error, we still want to know
                throw e
            }
        }

        // 6. Save to Cache
        renderCache[message] = renderedContent

        return renderedContent
    }


    /*private fun ensureTableSpacing(md: String): String {
        val pattern = Regex(
            """(^[\t >]*([-+*]|\d+\.)\s+(?:\\\$\\\[ ?[ xX]?\\]\\\s+)?[^\n]*)\n(?=\|)""",
            RegexOption.MULTILINE
        )
        return md.replace(pattern) { "${it.value}\n\n" }
    }*/
    fun clearCache() {
        renderCache.clear()
        collapsedStates.clear()
    }
    fun getLatestPlainText(): String? {
        return messages.lastOrNull()?.let { getMessageText(it.content) }
    }

    /*  ANDROID STUDIO gives issues with this but never had prob. grok 4.1 said above it good private fun ensureTableSpacing(md: String): String {
         // Detect any list marker (unordered, ordered, task) followed by a table start
         val pattern = Regex("""(^[\t >]*([-+*]|\d+\.)\s+(?:$$[ xX]$$\s+)?[^\n]*)\n(?=\|)""")
         return md.replace(pattern) { matchResult ->
             "${matchResult.value}\n\n"
         }
     }*/

    /* old works keep lumo gave above as improvement private fun ensureTableSpacing(markdown: String): String {
        val pattern = Regex("([^\\n])\\n(\\s*\\|.*\\|\\n)(\\s*\\|[\\s:-]+\\|)")
        return markdown.replace(pattern, "$1\n\n$2$3")
    }*/

    // --- PUBLIC METHODS ---

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
        if (isUserApplyingEdit) {
            applyEditUpdate(newMessages)
            return // Stop here, don't run the rest
        }
        if (newMessages.isEmpty() || (messages.isEmpty() && newMessages.isNotEmpty())) {
            renderCache.clear()
        }

        if (newMessages.isEmpty()) {
            messages.clear()
            notifyDataSetChanged()
            return
        }

        // Case 1: PERFECT - Only 1 new message added
        if (messages.size == newMessages.size - 1 &&
            messages == newMessages.dropLast(1)) {
            addMessage(newMessages.last())
            return
        }

        // Case 2: STREAMING - Same size, only last message content changed
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
    private fun applyEditUpdate(newMessages: List<FlexibleMessage>) {
        // 3. Use the stored position directly (Fast!)
        val index = editTargetPosition

        // Safety check: ensure index is valid
        if (index != -1 && index < messages.size && index < newMessages.size) {
            val oldMsg = messages[index]

            // 4. Clear cache
            renderCache.remove(oldMsg)

            // 5. Update list
            messages[index] = newMessages[index]

            // 6. Notify
            notifyItemChanged(index)
        }

        // 4. Reset the flag
        isUserApplyingEdit = false
        editTargetPosition = -1
    }
    fun removeLastMessage() {
        if (messages.isNotEmpty()) {
            val lastIndex = messages.size - 1
            messages.removeAt(lastIndex)
            notifyItemRemoved(lastIndex)
        }
    }

    // 4. OPTIMIZATION: Send to channel instead of direct update
    fun updateLastMessage(newMessage: FlexibleMessage) {
        if (messages.isNotEmpty()) {
            val oldMessage = messages.last()
            renderCache.remove(oldMessage)
        }
        updateChannel.trySend(newMessage)

    }

    // --- VIEW HOLDER LOGIC ---

    /*override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when (message.role) {
            "user" -> VIEW_TYPE_USER
            "assistant" -> {
                val content = (message.content as? JsonPrimitive)?.content ?: ""
                if (content == "working...") VIEW_TYPE_THINKING else VIEW_TYPE_ASSISTANT
            }
            else -> VIEW_TYPE_ASSISTANT
        }
    }*/
    override fun getItemViewType(position: Int): Int {
        val message = messages[position]

        // 1. ONLY hide the raw tool results (the giant data dump)
        if (message.role == "tool") return VIEW_TYPE_HIDDEN

        // 2. Do NOT hide the assistant's tool calls anymore.
        val contentText = getMessageText(message.content)

        return when (message.role) {
            "user" -> VIEW_TYPE_USER
            "assistant" -> {
                if (contentText == "working...") VIEW_TYPE_THINKING else VIEW_TYPE_ASSISTANT
            }
            else -> VIEW_TYPE_ASSISTANT
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HIDDEN -> { // <--- ADD THIS BLOCK
                val emptyView = View(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(0, 0)
                    visibility = View.GONE
                }
                HiddenViewHolder(emptyView)
            }
            VIEW_TYPE_USER -> {
                val view = inflater.inflate(R.layout.item_message_user, parent, false)
                view.findViewById<TextView>(R.id.messageTextView)
                    .setSpannableFactory(noCopyFactory)
                UserViewHolder(view, markwon)
                //UserViewHolder(view, markwon)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_message_ai, parent, false)
                // AssistantViewHolder(view, markwon, onSpeakText, onSynthesizeToWavFile)
                view.findViewById<TextView>(R.id.messageTextView)
                    .setSpannableFactory(noCopyFactory)
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
        // Otherwise do the heavy lifting
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        var contentText = getMessageText(message.content)

        if (message.role == "assistant" && message.toolCalls != null && contentText.isBlank()) {
            contentText = "🔧 **Tool Used:**  ${message.toolCalls.map { it.function.name }.distinct().joinToString()}"
        }

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
            messageTextView.textSize = 16f * currentFontScale / 100f
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
                    val msgKey = rawUserContent.hashCode().toString() // Stable!
                    val isCollapsed = collapsedStates.getOrDefault(msgKey, true)  // User: collapsed
                    val displayContent = if (isCollapsed) {
                        if (charLength > MAX_CHARS_THRESHOLD) {
                            // Char-truncate (paragraphs) + word-break
                            val cutOffIndex =
                                rawUserContent.take(MAX_CHARS_THRESHOLD).lastIndexOf(' ')
                            val safeIndex = if (cutOffIndex > 0) cutOffIndex else MAX_CHARS_THRESHOLD
                            rawUserContent.take(safeIndex) + "...(continued)"
                        } else {
                            // Line-truncate (lists/code)
                            rawUserContent.lines().take(MAX_LINES_THRESHOLD).joinToString("\n") + "\n\n**...(continued)**"
                        }
                    } else {
                        rawUserContent
                    }

                    // YOUR original try-catch (Prism4j-safe!)
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
                        this@ChatAdapter.notifyItemChanged(bindingAdapterPosition)
                        onCollapse()
                    }
                } else {
                    // Short: full Markwon
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
                // Non-user: full
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

            // ... (Keep the rest of your existing image/button logic below exactly as it was) ...
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

            copyButtonuser.setOnClickListener { //rawUserContent
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Copied Text", rawUserContent)
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
                if (rawUserContent.isNotBlank()) {
                    onEditMessage(bindingAdapterPosition, rawUserContent)
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
        private val reasoningContainer: ConstraintLayout = itemView.findViewById(R.id.reasoningContainer)
        private val reasoningTextView: TextView = itemView.findViewById(R.id.reasoningTextView)
        private val reasoningToggleButton: ImageButton = itemView.findViewById(R.id.reasoningToggleButton)
        private val reasoningCopyButton: ImageButton = itemView.findViewById(R.id.reasoningCopyButton)
        private val reasoningHeader: TextView = itemView.findViewById(R.id.reasoningHeader)

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
        private val saveFileButton: ImageButton = itemView.findViewById(R.id.saveFileButton)
        private val editButton: ImageButton = itemView.findViewById(R.id.editButton)
        private val CHAR_THRESHOLD = 350
        // 6. OPTIMIZATION: Lightweight bind for streaming
        fun bindTextOnly(message: FlexibleMessage) {
            /*un rleaiable text only
               val text = if (message.role == "assistant" && message.toolCalls != null && getMessageText(message.content).isBlank()) {
                   "Tool Call: ${message.toolCalls.map { it.function.name }.distinct().joinToString()}"
               } else {
                   getMessageText(message.content)
               }

               val reasoningText = message.reasoning?.let { "\n\n$it" } ?: ""
               val rawText = reasoningText + text
               val fullText = ensureTableSpacing(rawText)

               try {
                   markwon.setMarkdown(messageTextView, fullText)
               } catch (e: RuntimeException) {
                   if (e.message?.contains("Prism4j") == true || e.message?.contains("entry nodes") == true) {
                       messageTextView.text = fullText
                   }
               }
                */
            /*val text = getMessageText(message.content)
            val reasoning = message.reasoning
            // Logic: Only add newlines if we actually have reasoning text
            val displayText = if (!reasoning.isNullOrBlank()) {
                "$reasoning\n\n$text"
            } else {
                text
            }
            messageTextView.text = displayText*/
            messageTextView.text = getMessageText(message.content)
            bindReasoning(message)

        }

        fun bind(message: FlexibleMessage, position: Int, isSpeaking: Boolean, currentPosition: Int) {
            bindReasoning(message)
            messageTextView.textSize = 16f * currentFontScale / 100f
            messageTextView.typeface = currentTypeface

            // 1. DISPLAY TEXT (Optimized: Uses Cache)
            val finalContent = getPreRenderedContent(message)
            // messageTextView.text = finalContent
            markwon.setParsedMarkdown(messageTextView, finalContent as android.text.Spanned)
            // 2. LOGIC TEXT (Fast extraction)
            val text = if (message.role == "assistant" && message.toolCalls != null && getMessageText(message.content).isBlank()) {
                "Tool Call: ${message.toolCalls.map { it.function.name }.distinct().joinToString()}"
            } else {
                getMessageText(message.content)
            }

            // --- NEW COLLAPSE LOGIC (INSTANT, NO POST DELAY) ---
            // We use the raw text length to decide if it's collapsible.
            // This is O(1) speed (instant), vs waiting for layout.
            if (text.length > CHAR_THRESHOLD) {
                // Use a stable key for the map (message content hash is good enough here)
                val msgKey = text.hashCode().toString()

                // Default to FALSE (Expanded) for AI, or TRUE (Collapsed) if you prefer.
                // Your previous code suggested AI starts expanded (false).
                val isCollapsed = collapsedStates.getOrDefault(msgKey, false)

                // Apply state immediately
                applyCollapseState(isCollapsed)

                collapseToggleButton.visibility = View.VISIBLE
                collapseToggleButton.setImageResource(
                    if (isCollapsed) R.drawable.ic_expand_more else R.drawable.ic_expand_less2
                )

                collapseToggleButton.setOnClickListener {
                    val newState = !collapsedStates.getOrDefault(msgKey, false)
                    collapsedStates[msgKey] = newState

                    // Apply change immediately to the view
                    applyCollapseState(newState)
                    collapseToggleButton.setImageResource(
                        if (newState) R.drawable.ic_expand_more else R.drawable.ic_expand_less2
                    )
                    onCollapse()
                    // Note: We do NOT call notifyItemChanged here.
                    // Changing maxLines directly is smoother and doesn't re-trigger Markdown parsing.
                }
            } else {
                // Short message: Always expanded, hide button
                messageTextView.maxLines = Int.MAX_VALUE
                messageTextView.ellipsize = null
                collapseToggleButton.visibility = View.GONE
                collapseToggleButton.setOnClickListener(null)
            }
            // ---------------------------------------------------

            val cleanReasoning = getCleanReasoning(message)
            val reasoningText = cleanReasoning.takeIf { it.isNotBlank() }
                ?.let { "```\n$it\n```\n\n---\n\n" }
                ?: ""


            // 3. UI STATE LOGIC
            ttsButton.visibility = if (ttsAvailable) View.VISIBLE else View.GONE

            val isError = message.role == "assistant" && text.startsWith("**Error:**")
            val isThinking = text == "working..."
            val hasActiveTools = message.toolCalls != null && message.toolCalls.isNotEmpty()

            if (isError) {
                messageContainer.setBackgroundResource(R.drawable.bg_error_message)
            } else if (hasActiveTools) {
                messageContainer.setBackgroundResource(R.drawable.bg_ai_message_outlined)
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
                // val fullRawMarkdown = ensureTableSpacing(reasoningText + text)
                val fullRawMarkdown = reasoningText + text
                if (fullRawMarkdown.isNotBlank()) {
                    onShowMarkdown.invoke(fullRawMarkdown)
                }
            }
            htmlButton.setOnLongClickListener {
                // val fullRawMarkdown = ensureTableSpacing(reasoningText + text)
                val fullRawMarkdown = reasoningText + text
                if (fullRawMarkdown.isNotBlank()) {
                    onSaveHtml.invoke(fullRawMarkdown)
                    true // Consume long press
                } else false
            }

            copyButton.setOnClickListener {
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Copied Text", messageTextView.text.toString())
                clipboard.setPrimaryClip(clip)
            }
            editButton.setOnClickListener {
                // Pass the position and the raw text to the fragment
                //  val fullRawMarkdown = ensureTableSpacing(reasoningText + text)
                val fullRawMarkdown =  text
                onEditAssistantMessage(bindingAdapterPosition, fullRawMarkdown)
            }

            copyButton.setOnLongClickListener {
                // val fullRawMarkdown = ensureTableSpacing(reasoningText + text)
                val fullRawMarkdown = reasoningText + text
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
                //val fullRawMarkdown = ensureTableSpacing(reasoningText + text)
                val fullRawMarkdown = reasoningText + text
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
                // val fullRawMarkdown = ensureTableSpacing(reasoningText + text)
                val fullRawMarkdown = reasoningText + text
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
                        val context = itemView.context

                        // Disable StrictMode check for file:// URI
                        try {
                            val m = StrictMode::class.java.getMethod("disableDeathOnFileUriExposure")
                            m.invoke(null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        // Define the target folder path (keeping 'oxproxion')
                        val path = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "oxproxion")

                        // Create intent to view the folder
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setDataAndType(Uri.fromFile(path), "resource/folder")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                        // Create the system chooser intent
                        val chooserIntent = Intent.createChooser(intent, "Open Folder")

                        // Show Snackbar with the action
                        Snackbar.make(itemView, "PDF saved to Downloads", Snackbar.LENGTH_LONG)
                            .setAction("Open Folder") {
                                context.startActivity(chooserIntent)
                            }
                            .show()
                    } else {
                        // Keep the failure toast as it provides immediate error feedback
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
                //    val fullRawMarkdown = ensureTableSpacing(reasoningText + text)
                val fullRawMarkdown = reasoningText + text
                onSaveMarkdown(bindingAdapterPosition, fullRawMarkdown)
            }

            markdownButton.setOnLongClickListener {
                onSaveText(bindingAdapterPosition, messageTextView.text.toString())
                true
            }
            saveFileButton.setOnClickListener {
                //  onSaveAsFile.invoke(messageTextView.text.toString())
                onSaveAsFile.invoke(text)
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
        private fun bindReasoning(message: FlexibleMessage) {
            val reasoning = getCleanReasoning(message)
            // Read live — hide only when Advanced Reasoning is ON and "Include in response" is OFF
            val prefs = SharedPreferencesHelper(itemView.context)
            val suppress = prefs.getAdvancedReasoningEnabled() && prefs.getReasoningExclude()
            if (reasoning.isBlank() || suppress) {
                reasoningContainer.visibility = View.GONE
                return
            }
            reasoningContainer.visibility = View.VISIBLE
            reasoningTextView.text = reasoning
            reasoningTextView.textSize = 13f * currentFontScale / 100f
            reasoningTextView.typeface = Typeface.create(currentTypeface, Typeface.ITALIC)


            val msgKey = "reasoning_${reasoning.hashCode()}"
            val isCollapsed = collapsedStates.getOrDefault(msgKey, true) // default COLLAPSED

            applyReasoningCollapse(isCollapsed)
            reasoningToggleButton.setImageResource(
                if (isCollapsed) R.drawable.ic_expand_more else R.drawable.ic_expand_less2
            )

            val toggle = {
                val newState = !collapsedStates.getOrDefault(msgKey, true)
                collapsedStates[msgKey] = newState
                applyReasoningCollapse(newState)
                reasoningToggleButton.setImageResource(
                    if (newState) R.drawable.ic_expand_more else R.drawable.ic_expand_less2
                )
            }
            reasoningToggleButton.setOnClickListener { toggle() }
            reasoningHeader.setOnClickListener { toggle() }  // tap the header too

            reasoningCopyButton.setOnClickListener {
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Copied Reasoning", reasoning))
            }
        }

        private fun applyReasoningCollapse(collapsed: Boolean) {
            reasoningTextView.maxLines = if (collapsed) 3 else Int.MAX_VALUE
            reasoningTextView.ellipsize = if (collapsed) android.text.TextUtils.TruncateAt.END else null
        }

        private fun getCleanReasoning(message: FlexibleMessage): String {
            var r = message.reasoning ?: return ""
            r = r.trim()
            if (r.startsWith("```")) {           // legacy format only
                r = r.removePrefix("```").trim()
                val fence = r.lastIndexOf("```")
                if (fence != -1) r = r.substring(0, fence)
            }
            // raw format (new): untouched, internal backticks preserved
            return r.replace(Regex("\\n*-{3,}\\s*$"), "").trim()
        }



    }
    inner class HiddenViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

}