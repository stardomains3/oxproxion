package io.github.stardomains3.oxproxion

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.doOnPreDraw
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray


class ChatFragment : Fragment(R.layout.fragment_chat) {
    private lateinit var speechLauncher: ActivityResultLauncher<Intent>
    private lateinit var textToSpeech: TextToSpeech
    private var isSpeaking = false
    private var currentSpeakingPosition = -1
    private lateinit var helpButton: MaterialButton
    private var selectedImageBytes: ByteArray? = null
    private var selectedImageMime: String? = null
    private lateinit var plusButton: MaterialButton
    private lateinit var genButton: MaterialButton
    private lateinit var biometricButton: MaterialButton
    private var originalSendIcon: Drawable? = null
    private val viewModel: ChatViewModel by activityViewModels()
    private lateinit var modelNameTextView: TextView
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var chatEditText: EditText
    private lateinit var sendChatButton: MaterialButton
    private lateinit var resetChatButton: MaterialButton
    private lateinit var utilityButton: MaterialButton
    private lateinit var clearButton: MaterialButton
    private lateinit var speechButton: MaterialButton
    private lateinit var extendButton: MaterialButton
    private lateinit var convoButton: MaterialButton
    private lateinit var saveChatButton: MaterialButton
    private lateinit var openSavedChatsButton: MaterialButton
    private lateinit var copyChatButton: MaterialButton
    private lateinit var menuButton: MaterialButton
    private lateinit var saveapiButton: MaterialButton
    private lateinit var pdfChatButton: MaterialButton
    private lateinit var systemMessageButton: MaterialButton
    private lateinit var streamButton: MaterialButton
    private lateinit var reasoningButton: MaterialButton
    private var ttsAvailable = true
    private lateinit var setMaxTokensButton: MaterialButton
    private lateinit var notiButton: MaterialButton
    private lateinit var buttonsContainer: LinearLayout
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var markwon: Markwon
    private lateinit var pdfGenerator: PdfGenerator
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private lateinit var attachmentPreviewContainer: View
    private lateinit var previewImageView: ImageView
    private lateinit var removeAttachmentButton: ImageButton
    private lateinit var headerContainer: LinearLayout
    private var overlayView: View? = null
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private var currentCameraUri: Uri? = null
    private lateinit var cameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var imagePicker: ActivityResultLauncher<Intent>  // Renamed for clarity (gallery picker)

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferencesHelper = SharedPreferencesHelper(requireContext())
        speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!results.isNullOrEmpty()) {
                    val recognizedText = results[0]
                    chatEditText.setText(recognizedText)
                    chatEditText.setSelection(chatEditText.text.length)
                    if (sharedPreferencesHelper.getConversationModeEnabled()) {
                        sendChatButton.performClick()
                    }

                    //   updateButtonVisibility()  // Update your buttons
                }
            }
        }
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startSpeechRecognition()  // Auto-start after grant
            } else {
                Toast.makeText(requireContext(), "Microphone permission needed for voice input", Toast.LENGTH_SHORT).show()
            }
        }
        cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                launchCamera()
            } else {
                Toast.makeText(requireContext(), "Camera permission needed to take photo", Toast.LENGTH_SHORT).show()
            }
        }

        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val imageUri = currentCameraUri ?:
            run {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    result.data?.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    result.data?.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)
                }
            } ?: result.data?.data  // Fallbacks

            if (result.resultCode == Activity.RESULT_OK && imageUri != null) {
                try {
                    // Read raw bytes first (fresh stream, one-time read)
                    val rawBytes = requireContext().contentResolver.openInputStream(imageUri)?.use { stream ->
                        stream.readBytes()
                    } ?: run {
                        Toast.makeText(requireContext(), "Failed to read image", Toast.LENGTH_SHORT).show()
                        return@registerForActivityResult
                    }

                    if (rawBytes.size > 12_000_000) {
                        Toast.makeText(requireContext(), "Image too large (max 12MB)", Toast.LENGTH_SHORT).show()
                        requireContext().contentResolver.delete(imageUri, null, null)
                        return@registerForActivityResult
                    }

                    selectedImageBytes = rawBytes  // Raw for send (EXIF intact)
                    selectedImageMime = "image/jpeg"
                    previewImageView.setImageURI(imageUri)  // Use Uri for preview (EXIF auto)
                    attachmentPreviewContainer.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "Photo saved to gallery", Toast.LENGTH_SHORT).show()

                    // NEW: Set pending as string for FlexibleMessage (MediaStore Uri already persistent)
                    viewModel.setPendingUserImageUri(imageUri.toString())

                    // Notify for gallery refresh
                    requireContext().contentResolver.notifyChange(imageUri, null)
                } catch (e: Exception) {
                    Log.e("ChatFragment", "Error processing photo: ${e.message}", e)
                    Toast.makeText(requireContext(), "Failed to process photo", Toast.LENGTH_SHORT).show()
                    requireContext().contentResolver.delete(imageUri, null, null)
                }
            } else {
                // Cancel or error
                Toast.makeText(requireContext(), if (result.resultCode == Activity.RESULT_CANCELED) "Capture canceled" else "Capture failed", Toast.LENGTH_SHORT).show()
                imageUri?.let { uri ->
                    requireContext().contentResolver.delete(uri, null, null)  // Clean up placeholder
                }
            }
            currentCameraUri = null  // Always reset after callback
        }


        imagePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri: Uri? = result.data?.data
                uri?.let { u ->
                    requireContext().contentResolver.openInputStream(u)?.use { stream ->
                        val bytes = stream.readBytes()
                        if (bytes.size > 12_000_000) {
                            Toast.makeText(requireContext(), "Image too large (max 12MB)", Toast.LENGTH_SHORT).show()
                            return@use
                        }
                        val mime = requireContext().contentResolver.getType(u)
                        when (mime) {
                            "image/jpeg", "image/png", "image/webp" -> {
                                // Valid MIME type - proceed
                            }
                            else -> {
                                Toast.makeText(requireContext(), "Unsupported image format", Toast.LENGTH_SHORT).show()
                                return@use
                            }
                        }
                        selectedImageBytes = bytes
                        selectedImageMime = mime
                        previewImageView.setImageURI(u)
                        attachmentPreviewContainer.visibility = View.VISIBLE

                        // NEW: Make URI persistent (no copy/save)
                        try {
                            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            requireContext().contentResolver.takePersistableUriPermission(u, takeFlags)

                            // Set pending as string for FlexibleMessage
                            viewModel.setPendingUserImageUri(u.toString())

                            Log.d("ChatFragment", "Persistent URI granted for gallery: $u")
                        } catch (e: SecurityException) {
                            Log.e("ChatFragment", "Persistent permission failed: ${e.message}", e)
                            Toast.makeText(requireContext(), "Image access limited; tap won't open full file", Toast.LENGTH_SHORT).show()
                            // Fallback: No Uri set, use base64 display only
                        }
                    }
                }
            }
        }
        // --- Initialize Views from fragment_chat.xml ---
        pdfChatButton = view.findViewById(R.id.pdfChatButton)
        systemMessageButton = view.findViewById(R.id.systemMessageButton)
        streamButton = view.findViewById(R.id.streamButton)
        reasoningButton = view.findViewById(R.id.reasoningButton)
        notiButton = view.findViewById(R.id.notiButton)
        extendButton = view.findViewById(R.id.extendButton)
        convoButton  = view.findViewById(R.id.convoButton)
        clearButton = view.findViewById(R.id.clearButton)
        utilityButton = view.findViewById(R.id.utilityButton)
        speechButton = view.findViewById(R.id.speechButton)
        chatRecyclerView = view.findViewById(R.id.chatRecyclerView)
        chatEditText = view.findViewById(R.id.chatEditText)
        sendChatButton = view.findViewById(R.id.sendChatButton)
        originalSendIcon = sendChatButton.icon
        resetChatButton = view.findViewById(R.id.resetChatButton)
        saveChatButton = view.findViewById(R.id.saveChatButton)
        openSavedChatsButton = view.findViewById(R.id.openSavedChatsButton)
        copyChatButton = view.findViewById(R.id.copyChatButton)
        menuButton = view.findViewById(R.id.menuButton)
        saveapiButton = view.findViewById(R.id.saveapiButton)
        setMaxTokensButton = view.findViewById(R.id.setMaxTokensButton)
        buttonsContainer = view.findViewById(R.id.buttonsContainer)
        modelNameTextView = view.findViewById(R.id.modelNameTextView)
        attachmentPreviewContainer = view.findViewById(R.id.attachmentPreviewContainer)
        previewImageView = view.findViewById(R.id.previewImageView)
        removeAttachmentButton = view.findViewById(R.id.removeAttachmentButton)
        headerContainer = view.findViewById(R.id.headerContainer)
        helpButton = view.findViewById(R.id.helpButton)
        arguments?.getString("shared_text")?.let { sharedText ->
            setSharedText(sharedText)
            arguments?.remove("shared_text") // To prevent re-processing
        }
        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        requireActivity().runOnUiThread { onSpeechFinished() }  // Run on main thread
                    }
                    override fun onError(utteranceId: String?) {
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "TTS error: Check TTS settings or engine", Toast.LENGTH_SHORT).show()
                            onSpeechFinished()
                        }
                    }
                })
                ttsAvailable = true
            } else {
                Toast.makeText(requireContext(), "TTS failed", Toast.LENGTH_SHORT).show()
                ttsAvailable = false
            }
        }
        val prism4j = Prism4j(ExampleGrammarLocator())
        // val theme = Prism4jThemeDefault.create()
        val theme = Prism4jThemeDarkula.create()
        val syntaxHighlightPlugin = SyntaxHighlightPlugin.create(prism4j, theme)
        markwon = Markwon.builder(requireContext())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(syntaxHighlightPlugin)
            .usePlugin(TablePlugin.create(requireContext()))      // <-- Add Tables plugin
            .usePlugin(TaskListPlugin.create(requireContext()))    // <-- Add Task List plugin
            .usePlugin(CoilImagesPlugin.create(requireContext()))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .codeTextColor(Color.LTGRAY)
                        .codeBackgroundColor(Color.BLACK)
                        .codeBlockBackgroundColor(Color.BLACK)
                        .blockQuoteColor(Color.BLACK)
                        .isLinkUnderlined(true)
                }
            })
            .build()
        setupRecyclerView()

        // In onViewCreated(), after initializing chatEditText and before setupClickListeners()
        chatEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (headerContainer.isVisible && count > 0) { // Hide on first character input (touch on key)
                    hideMenu()
                    // Optional: chatEditText.removeTextChangedListener(this) // Remove after first hide
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {
                updateButtonVisibility()
            }
        })
        pdfGenerator = PdfGenerator(requireContext())
        plusButton = view.findViewById(R.id.plusButton)
        genButton = view.findViewById(R.id.genButton)
        biometricButton = view.findViewById(R.id.biometricButton)
        setupClickListeners()
        setupPlusButtonListener()
        updateSystemMessageButtonState()
        updateInitialUI()
        val rootView = view as FrameLayout // The root FrameLayout (fragment_container)
        overlayView = View(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            setBackgroundColor(requireContext().getColor(android.R.color.transparent))
            // This listener logic is now correct
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    if (headerContainer.isVisible && isTouchOutsideHeader(event.rawX, event.rawY) && !isTouchOnMenuButton(event.rawX, event.rawY)) {
                        hideMenu()
                        // Return false to pass the touch to the button underneath
                        return@setOnTouchListener false
                    }
                }
                // If touch is inside the header, let it pass through to the header
                false
            }
        }
        rootView.addView(overlayView)

        viewModel.activeChatModel.observe(viewLifecycleOwner) { model ->
            if (model != null) {
                modelNameTextView.text = viewModel.getModelDisplayName(model)
               // modelNameTextView.text = formatModelName(model)
                plusButton.visibility = if (viewModel.isVisionModel(model)) View.VISIBLE else View.GONE
                genButton.visibility = if (viewModel.isImageGenerationModel(model)) View.VISIBLE else View.GONE
                reasoningButton.visibility = if (viewModel.isReasoningModel(model)) View.VISIBLE else View.GONE
                // BUG FIX START: Clear staged image if new model is not a vision model
                if (selectedImageBytes != null && !viewModel.isVisionModel(model)) {
                    selectedImageBytes = null
                    selectedImageMime = null
                    attachmentPreviewContainer.visibility = View.GONE
                    Toast.makeText(requireContext(), "Image removed: selected model doesn't support images.", Toast.LENGTH_SHORT).show()
                }
                // BUG FIX END
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sharedText.filterNotNull().collect { text ->
                    // do whatever you need
                    setSharedText(text)
                    viewModel.textConsumed()
                }
            }
        }
        // --- Observe LiveData ---
        viewModel.chatMessages.observe(viewLifecycleOwner) { messages ->
            chatAdapter.setMessages(messages)
            //  chatRecyclerView.post { updateScrollButtons() }
            val hasMessages = messages.isNotEmpty()
            if(hasMessages){
                resetChatButton.icon.alpha = 255
                saveChatButton.icon.alpha = 255
            }
            else
            {
                resetChatButton.icon.alpha = 102
                saveChatButton.icon.alpha = 102
            }

            saveChatButton.isEnabled = hasMessages
            resetChatButton.isEnabled = hasMessages
            pdfChatButton.isVisible = hasMessages
            copyChatButton.isVisible = hasMessages


        }


        fun areAnimationsEnabled(context: Context): Boolean {
            val resolver = context.contentResolver
            return try {
                val durationScale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)
                durationScale != 0.0f
            } catch (e: Exception) {
                true // Default to true if we can't read settings
            }
        }

        viewModel.isAwaitingResponse.observe(viewLifecycleOwner) { isAwaiting ->
            if (!isAwaiting && sharedPreferencesHelper.getConversationModeEnabled()) {
                val messages = viewModel.chatMessages.value ?: return@observe
                if (messages.isNotEmpty()) {
                    val lastMessage = messages.last()
                    if (lastMessage.role == "assistant" &&
                        viewModel.getMessageText(lastMessage.content) != "thinking...") {
                        chatRecyclerView.post {
                            val position = messages.size - 1
                            val holder = chatRecyclerView.findViewHolderForAdapterPosition(position) as? ChatAdapter.AssistantViewHolder
                            holder?.ttsButton?.performClick()
                        }
                    }
                }
            }
            sendChatButton.isEnabled = true

            val materialButton = sendChatButton
            if (isAwaiting) {
                materialButton.setIconResource(R.drawable.ic_stop)
            }
            else{
                materialButton.icon = originalSendIcon
            }
           /* if (isAwaiting) {
                if (areAnimationsEnabled(requireContext())) {
                    // Stop any existing animation first
                    // (materialButton.icon as? Animatable)?.stop()

                    // Set and start new animated drawable
                    val avd = AnimatedVectorDrawableCompat.create(requireContext(), R.drawable.avd_rotating_arc)
                    materialButton.icon = avd
                    avd?.start()
                }
                else {
                    // Fallback: show static arc or different icon when animations are off
                    materialButton.setIconResource(R.drawable.ic_stop) // or another static indicator
                }
            } else {
                // Stop animation and reset to original icon
                (materialButton.icon as? Animatable)?.stop()
                materialButton.icon = originalSendIcon
            }*/
        }

        viewModel.modelPreferenceToSave.observe(viewLifecycleOwner) { model ->
            model?.let {
                sharedPreferencesHelper.savePreferenceModelnewchat(it)
                viewModel.onModelPreferenceSaved()
            }
        }
        viewModel.autosendEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                // Perform autosend: Simulate send button click
                sendChatButton.performClick()
            }
        }

        // --- Credits Observer ---
        viewModel.creditsResult.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { resultMessage ->
                Toast.makeText(requireContext(), resultMessage, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.isStreamingEnabled.observe(viewLifecycleOwner) { isEnabled ->
            streamButton.isSelected = isEnabled
        }

        viewModel.isReasoningEnabled.observe(viewLifecycleOwner) { isEnabled ->
            reasoningButton.isSelected = isEnabled
            updateReasoningButtonAppearance()
        }
        viewModel.isAdvancedReasoningOn.observe(viewLifecycleOwner) { isAdvanced ->
            updateReasoningButtonAppearance() // Call helper
        }

        viewModel.isNotiEnabled.observe(viewLifecycleOwner) { isEnabled ->
            notiButton.isSelected = isEnabled
            if(isEnabled && !ForegroundService.isRunningForeground ){
                startForegroundService()
            }
            else if (!isEnabled && ForegroundService.isRunningForeground){
                stopForegroundService()
            }
        }
        viewModel.scrollToBottomEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                if (chatAdapter.itemCount > 0) {
                    chatRecyclerView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            chatRecyclerView.viewTreeObserver.removeOnPreDrawListener(this)

                            val layoutManager = chatRecyclerView.layoutManager as LinearLayoutManager
                            val position = chatAdapter.itemCount - 1

                            // Scroll with positive offset to show icon
                            // val offset = resources.getDimensionPixelSize(R.dimen.ai_icon_offset)
                            layoutManager.scrollToPositionWithOffset(position, -12)

                            return true
                        }
                    })
                }
            }
        }
        val shouldStartStt = arguments?.getBoolean("start_stt_on_launch", false) ?: false
        if (shouldStartStt) {
            arguments?.remove("start_stt_on_launch")  // Clear flag to prevent re-trigger
            chatEditText.hideKeyboard()  // Ensure clean state
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)  // NEW: Use launcher
            } else {
                startSpeechRecognition()  // Your existing method
            }
        }

    }

    private fun updateSystemMessageButtonState() {
        val selectedSystemMessage = sharedPreferencesHelper.getSelectedSystemMessage()
        systemMessageButton.isSelected = !selectedSystemMessage.isDefault
        updateChatEditTextHint()
    }

    private fun updateChatEditTextHint() {
        val selectedMessage = sharedPreferencesHelper.getSelectedSystemMessage()
        val isDefault = selectedMessage.isDefault
        val title = selectedMessage.title.trim()
        if (isDefault) {
            chatEditText.hint = "Type a message..."
        } else {
            chatEditText.hint = "($title) Type a message..."
        }
    }

    fun setSharedText(sharedText: String) {
        var spookyValue = sharedText
        val httpIndex = spookyValue.indexOf("http", 0, true)
        if (httpIndex != 0 && httpIndex != -1) {
            spookyValue = spookyValue.substring(httpIndex)
            val spaceIndex = spookyValue.indexOf(" ")
            if(spaceIndex != -1){
                spookyValue = spookyValue.substring(0, spaceIndex)
            }
            spookyValue = spookyValue.trim()
            spookyValue = spookyValue.trim('"')
        }
        spookyValue = spookyValue.trim()
        chatEditText.setText(spookyValue)
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(
            markwon,
            viewModel,
            { text, position -> speakText(text, position) },
            ttsAvailable,
            onEditMessage = { position, text ->
                // Existing edit confirmation dialog (unchanged from previous)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Edit this message?")
                    .setMessage("This will load the message into the prompt box for editing and remove it along with all following messages (AI responses and later prompts) from the chat history. This action cannot be undone.\n\nProceed?")
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setPositiveButton("Edit") { _, _ ->
                        viewModel.truncateHistory(position)
                        chatEditText.setText(text)
                        chatEditText.setSelection(text.length)
                        hideMenu()
                        chatEditText.requestFocus()
                        chatEditText.showKeyboard()
                        if (chatAdapter.itemCount > 0) {
                            chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                        }
                    }
                    .setCancelable(true)
                    .show()
            },
            // <-- NEW: Add this entire callback
            onRedoMessage = { position, originalContent ->
                // Show confirmation dialog for redo
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Resend this message?")
                    .setMessage("This will remove all following messages from the chat, then resend the prompt automatically to generate a new response. This action cannot be undone.\n\nProceed?")
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()  // Do nothing
                    }
                    .setPositiveButton("Redo") { _, _ ->
                        // Truncate from position + 1 (keep original user message, remove everything after)
                        viewModel.truncateHistory(position )
                        // Fetch current system message (as in sendChatButton logic)
                        val systemMessage = sharedPreferencesHelper.getSelectedSystemMessage().prompt
                        // Auto-resend the original content (triggers thinking bubble + new response)
                        viewModel.sendUserMessage(originalContent, systemMessage)
                        if (ForegroundService.isRunningForeground && sharedPreferencesHelper.getNotiPreference()) {
                            val apiIdentifier = viewModel.activeChatModel.value ?: "Unknown Model"
                            val displayName = viewModel.getModelDisplayName(apiIdentifier)
                            ForegroundService.updateNotificationStatusSilently(displayName, "Prompt sent. Awaiting Response.")
                        }
                        // UI polish: Hide menu, scroll to bottom (after resend starts)
                        hideMenu()
                        if (chatAdapter.itemCount > 0) {
                            chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)
                        }
                    }
                    .setCancelable(true)
                    .show()
            }
        )
        chatRecyclerView.apply {
            adapter = chatAdapter
            layoutManager = NonScrollingOnFocusLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
        }
    }

    fun View.showKeyboard() {
        doOnPreDraw {
            if (!isFocusable || !isFocusableInTouchMode) return@doOnPreDraw
            requestFocus()
            windowInsetsController?.show(WindowInsets.Type.ime())
        }
    }

    fun View.hideKeyboard() {
        doOnPreDraw {
            windowInsetsController?.hide(WindowInsets.Type.ime())
            requestFocus()
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        stopForegroundService()
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        removeAttachmentButton.setOnClickListener {
            selectedImageBytes = null
            selectedImageMime = null
            attachmentPreviewContainer.visibility = View.GONE
            viewModel.setPendingUserImageUri(null)
            Toast.makeText(requireContext(), "Attachment removed", Toast.LENGTH_SHORT).show()
        }
        sendChatButton.setOnClickListener {
            if (viewModel.isAwaitingResponse.value == true) {
                viewModel.cancelCurrentRequest()
                //   viewModel.playCancelTone()
            } else {
                // --- API Key Check ---
                if (viewModel.activeChatApiKey.isBlank()) {
                    Toast.makeText(requireContext(), "API Key is not set.", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener // Stop the process
                }

                val prompt = chatEditText.text.toString().trim()
                if (prompt.isNotBlank() || selectedImageBytes != null) {
                    /* if (!ForegroundService.isRunningForeground) {
                         ChatServiceGate.shouldRunService = true
                         startForegroundService()
                     }*/

                    if (ForegroundService.isRunningForeground && sharedPreferencesHelper.getNotiPreference()) {
                        val apiIdentifier = viewModel.activeChatModel.value ?: "Unknown Model"
                        val displayName = viewModel.getModelDisplayName(apiIdentifier)
                        ForegroundService.updateNotificationStatusSilently(displayName, "Prompt sent. Awaiting Response.")
                    }
                    chatEditText.setText("")
                    chatEditText.text.clear()
                    chatEditText.hideKeyboard()
                    val userContent = if (selectedImageBytes != null) {
                        val base64 = Base64.encodeToString(selectedImageBytes, Base64.NO_WRAP)
                        val imageUrl = "data:$selectedImageMime;base64,$base64"
                        buildJsonArray {
                            if (prompt.isNotBlank()) {
                                add(
                                    JsonObject(
                                        mapOf(
                                            "type" to JsonPrimitive("text"),
                                            "text" to JsonPrimitive(prompt)
                                        )
                                    )
                                )
                            }
                            add(
                                JsonObject(
                                    mapOf(
                                        "type" to JsonPrimitive("image_url"),
                                        "image_url" to JsonObject(
                                            mapOf(
                                                "url" to JsonPrimitive(imageUrl)
                                            )
                                        )
                                    )
                                )
                            )
                        }
                    } else {
                        JsonPrimitive(prompt)
                    }
                    val systemMessage = sharedPreferencesHelper.getSelectedSystemMessage()
                    viewModel.sendUserMessage(userContent, systemMessage.prompt)
                    chatEditText.clearFocus()
                    hideMenu()
                    selectedImageBytes = null
                    selectedImageMime = null
                    attachmentPreviewContainer.visibility = View.GONE
                    if (chatAdapter.itemCount > 0) {
                        chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                    }
                }
            }
        }
        genButton.setOnClickListener {
            val model = viewModel.activeChatModel.value
            if (model !in listOf("google/gemini-2.5-flash-image", "google/gemini-2.5-flash-image-preview")) {
                Toast.makeText(requireContext(), "Image generation parameters not supported for this model", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


            // Dialog options (match docs: 1:1, 16:9, etc.)
            val aspectRatios = arrayOf("1:1", "2:3", "3:2", "3:4", "4:3", "4:5", "5:4", "9:16", "16:9", "21:9")
            val currentRatio = sharedPreferencesHelper.getGeminiAspectRatio() ?: "1:1"  // Default 1:1
            val selectedIndex = aspectRatios.indexOf(currentRatio)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select Aspect Ratio")
                .setSingleChoiceItems(aspectRatios, selectedIndex) { _, which ->
                    val selectedRatio = aspectRatios[which]
                    sharedPreferencesHelper.saveGeminiAspectRatio(selectedRatio)
                  //  Toast.makeText(requireContext(), "Aspect ratio set to $selectedRatio", Toast.LENGTH_SHORT).show()
                }
                .setPositiveButton("OK") { _, _ -> /* Dialog dismisses */ }
                .setNegativeButton("Cancel", null)
                .show()
        }
        biometricButton.setOnClickListener {
            val prefs = SharedPreferencesHelper(requireContext())
            val currentlyOn = prefs.getBiometricEnabled()

            if (currentlyOn) {
                // simply turn off
                prefs.saveBiometricEnabled(false)
                biometricButton.isSelected = false
                return@setOnClickListener
            }

            // trying to turn on – check hardware
            val bm = BiometricManager.from(requireContext())
            when (bm.canAuthenticate(BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    prefs.saveBiometricEnabled(true)
                    biometricButton.isSelected = true
                    // Toast.makeText(requireContext(), "Biometric lock enabled", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(requireContext(), "No biometrics available", Toast.LENGTH_SHORT).show()
                }
            }
        }
        modelNameTextView.setOnClickListener {
            val picker = BotModelPickerFragment().apply {
                onModelSelected = { modelString ->
                    val newModelSupportsWebp = viewModel.supportsWebp(modelString)
                    val isStagedImageWebp = selectedImageMime == "image/webp"
                    val historyHasWebp = viewModel.hasWebpInHistory()
                    val hasImagesInCurrentChat =  viewModel.hasImagesInChat()
                    if (!newModelSupportsWebp && (isStagedImageWebp || historyHasWebp)) {
                        Toast.makeText(
                            requireContext(),
                            "Cannot switch: Model does not support WebP image in chat.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else if (hasImagesInCurrentChat && !viewModel.isVisionModel(modelString)) {
                        Toast.makeText(
                            requireContext(),
                            "Cannot switch: Model does not support images and current chat has images.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else
                    {
                        viewModel.setModel(modelString)
                        if (ForegroundService.isRunningForeground && sharedPreferencesHelper.getNotiPreference()) {
                            val apiIdentifier = viewModel.activeChatModel.value ?: "Unknown Model"
                            val displayName = viewModel.getModelDisplayName(apiIdentifier)
                            ForegroundService.updateNotificationStatusSilently(displayName, "Model Changed")
                        }
                    }
                }
            }
            parentFragmentManager.beginTransaction()
                .hide(this)
                .add(R.id.fragment_container, picker)
                .addToBackStack(null)
                .commit()
        }

        systemMessageButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .hide(this)
                .add(R.id.fragment_container, SystemMessageLibraryFragment())
                .addToBackStack(null)
                .commit()
        }
        resetChatButton.setOnLongClickListener {
            if (ForegroundService.isRunningForeground && sharedPreferencesHelper.getNotiPreference()) {
                val apiIdentifier = viewModel.activeChatModel.value ?: "Unknown Model"
                val displayName = viewModel.getModelDisplayName(apiIdentifier)
                ForegroundService.updateNotificationStatusSilently(displayName, "oxproxion is Ready.")
            }
            viewModel.startNewChat()
            true
        }
        resetChatButton.setOnClickListener {
            if (viewModel.chatMessages.value.isNullOrEmpty()) {
                return@setOnClickListener
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Start New Chat?")
                .setMessage("Are you sure you want to clear the current conversation? This action cannot be undone.")
                .setNegativeButton("Cancel") { dialog, which ->
                    dialog.dismiss()
                }
                .setPositiveButton("Reset") { dialog, which ->
                    if (ForegroundService.isRunningForeground && sharedPreferencesHelper.getNotiPreference()) {
                        val apiIdentifier = viewModel.activeChatModel.value ?: "Unknown Model"
                        val displayName = viewModel.getModelDisplayName(apiIdentifier)
                        ForegroundService.updateNotificationStatusSilently(displayName, "oxproxion is Ready.")
                    }
                    /*if (ForegroundService.isRunningForeground) {
                        stopForegroundService()
                    }
                    else{
                        ChatServiceGate.shouldRunService = false
                    }*/
                    viewModel.startNewChat()
                }
                .show()
        }

        saveChatButton.setOnClickListener {
            if (viewModel.chatMessages.value.isNullOrEmpty()) {
                return@setOnClickListener
            }
            else {
                showSaveChatDialogWithResultApi()
            }
        }

        openSavedChatsButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .hide(this)
                .add(R.id.fragment_container, SavedChatsFragment())
                .addToBackStack(null)
                .commit()

        }

        pdfChatButton.setOnClickListener {
            val messages = viewModel.chatMessages.value ?: emptyList()

            if (messages.isEmpty()) {
                Toast.makeText(requireContext(), "No chat history to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pdfChatButton.setIconResource(R.drawable.ic_check)
            Handler(Looper.getMainLooper()).postDelayed({
                hideMenu()
                pdfChatButton.setIconResource(R.drawable.ic_pdfnew)
            }, 500)

            lifecycleScope.launch {
                // Toast.makeText(requireContext(), "Generating PDF...", Toast.LENGTH_SHORT).show()
                val modelIdentifier = viewModel.activeChatModel.value ?: "Unknown Model"
                val modelName = viewModel.getModelDisplayName(modelIdentifier)
                val filePath = withContext(Dispatchers.IO) {
                    try {
                        val messages = viewModel.chatMessages.value ?: emptyList()
                        val generatedImagesMap = mutableMapOf<Int, String>()
                        messages.forEachIndexed { index, message ->
                            if (message.role == "assistant" && !message.imageUri.isNullOrEmpty()) {
                                generatedImagesMap[index] = message.imageUri  // Direct string (no parse)
                            }
                        }
                        when {
                            generatedImagesMap.isNotEmpty() -> {
                                // Case #3: Generated images (URIs from messages)
                                pdfGenerator.generateStyledChatPdfWithGeneratedImages(requireContext(), messages, modelName, generatedImagesMap)
                            }
                            viewModel.hasImagesInChat() -> {
                                // First case: Uploaded images (base64 in content)
                                pdfGenerator.generateStyledChatPdfWithImages(requireContext(), messages, modelName)
                            }
                            else -> {
                                // Second case: No images
                                val markdownText = viewModel.getFormattedChatHistory()
                                pdfGenerator.generateStyledChatPdf(requireContext(), markdownText, modelName)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ChatFragment", "PDF generation failed", e)
                        null
                    }
                }

                withContext(Dispatchers.Main) {
                    if (filePath != null) {
                        Toast.makeText(requireContext(), "PDF Created", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "PDF Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        chatEditText.setOnReceiveContentListener(
            arrayOf("text/*")
        ) { view, payload ->
            if (payload.clip != null) {
                val text = payload.clip.getItemAt(0).text?.toString() ?: ""
                val editable = chatEditText.editableText
                val start = chatEditText.selectionStart
                val end = chatEditText.selectionEnd
                editable.replace(start, end, text)
                null
            } else {
                payload
            }
        }

        copyChatButton.setOnClickListener {
            val chatText = viewModel.getFormattedChatHistoryPlainText()  // Use the new plain-text function
            if (chatText.isNotBlank()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Chat History", chatText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Chat Copied!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Nothing to Copy", Toast.LENGTH_SHORT).show()
            }
        }


        menuButton.setOnClickListener {
            if (headerContainer.isVisible) {
                hideMenu()
            } else {
                showMenu()
            }
        }
        menuButton.setOnLongClickListener {
            val inputText = chatEditText.text.toString().trim()
            if (inputText.isBlank()) {
                Toast.makeText(requireContext(), "No text to correct", Toast.LENGTH_SHORT).show()
            } else if (viewModel.activeChatApiKey.isBlank()) {
                Toast.makeText(requireContext(), "API Key is not set.", Toast.LENGTH_SHORT).show()
            } else {
                menuButton.isSelected = true
                menuButton.setIconResource(R.drawable.ic_magic)

                lifecycleScope.launch {
                    val corrected = viewModel.correctText(inputText)
                    if (corrected != null && corrected.isNotBlank()) {
                        chatEditText.setText(corrected)
                        chatEditText.setSelection(corrected.length)
                    } else {
                        Toast.makeText(

                            requireContext(),
                            "Correction failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    menuButton.setIconResource(R.drawable.ic_menudot)
                    menuButton.isSelected = false
                }
            }
            true
        }
        helpButton.setOnClickListener {
            hideMenu()
            parentFragmentManager.beginTransaction()
                .hide(this)
                .add(R.id.fragment_container, HelpFragment())
                .addToBackStack(null)
                .commit()
        }
        reasoningButton.setOnLongClickListener {
            if(reasoningButton.isSelected)
            {
                parentFragmentManager.beginTransaction()
                    .hide(this)
                    .add(R.id.fragment_container, AdvancedReasoningFragment())
                    .addToBackStack(null)
                    .commit()
            }
            return@setOnLongClickListener true
        }
        saveapiButton.setOnClickListener {
            hideMenu()
            val dialog = SaveApiDialogFragment()
            dialog.show(childFragmentManager, "SaveApiDialogFragment")
        }
        setMaxTokensButton.setOnClickListener {
            hideMenu()
            val dialog = MaxTokensDialogFragment()
            dialog.show(childFragmentManager, "MaxTokensDialogFragment")
        }

        saveapiButton.setOnLongClickListener {
            hideMenu()
            if (viewModel.activeChatApiKey.isBlank()) {
                Toast.makeText(requireContext(), "API Key is not set.", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.checkRemainingCredits()
            }
            true // Consume the long click
        }
        modelNameTextView.setOnLongClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, "https://openrouter.ai/models".toUri())
                startActivity(intent)
            } catch (e: Exception) {
                // Handle case where a web browser is not available
                Toast.makeText(requireContext(), "Could not open browser.", Toast.LENGTH_SHORT).show()
            }
            true // Consume the long click
        }

        streamButton.setOnClickListener {
            viewModel.toggleStreaming()
        }

        reasoningButton.setOnClickListener {
            viewModel.toggleReasoning()
        }
        notiButton.setOnClickListener {

            viewModel.toggleNoti()
        }
        convoButton.setOnClickListener {
            val currentState = sharedPreferencesHelper.getConversationModeEnabled()
            val newState = !currentState
            sharedPreferencesHelper.saveConversationModeEnabled(newState)
            convoButton.isSelected = newState
        }
        extendButton.setOnClickListener {

            val currentState = sharedPreferencesHelper.getExtPreference()
            val newState = !currentState
            sharedPreferencesHelper.saveExtPreference(newState)

            // Update UI directly (same logic as updateInitialUI)
            extendButton.isSelected = newState
            utilityButton.visibility = if (newState) View.VISIBLE else View.GONE

            val hasText = !chatEditText.text.isNullOrEmpty()
            if (newState) {
                if (hasText) {
                    clearButton.visibility = View.VISIBLE
                    speechButton.visibility = View.GONE
                } else {
                    clearButton.visibility = View.GONE
                    speechButton.visibility = View.VISIBLE
                }
            } else {
                clearButton.visibility = View.GONE
                speechButton.visibility = View.GONE
            }
        }
        systemMessageButton.setOnLongClickListener {
            val defaultMessage = SharedPreferencesHelper(requireContext()).getDefaultSystemMessage()
            SharedPreferencesHelper(requireContext()).saveSelectedSystemMessage(defaultMessage)
            systemMessageButton.isSelected = false
            updateChatEditTextHint()
            // Toast.makeText(requireContext(), "System message reset to default", Toast.LENGTH_SHORT).show()
            true
        }
        sendChatButton.setOnLongClickListener {
           /* if (!chatEditText.text.isBlank()) {
                chatEditText.text.clear()
            }*/
            chatEditText.setText("")
            chatEditText.text.clear()

            true
        }
        utilityButton.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val item = clip.getItemAt(0)
                val text = item.text
                if (text != null) {
                    // Safe to paste as text
                    val start = chatEditText.selectionStart
                    val end = chatEditText.selectionEnd
                    chatEditText.text.replace(start, end, text.toString())
                } else {
                    // Clipboard item is not text (e.g., image, URI, etc.)
                    Toast.makeText(requireContext(), "Clipboard does not contain text", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Nothing to paste", Toast.LENGTH_SHORT).show()
            }
        }

        utilityButton.setOnLongClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val item = clip.getItemAt(0)
                val text = item.text
                if (text != null) {
                    // Safe to paste as text
                    val start = chatEditText.selectionStart
                    val end = chatEditText.selectionEnd
                    chatEditText.text.replace(start, end, text.toString())
                    sendChatButton.performClick()
                } else {
                    // Clipboard item is not text (e.g., image, URI, etc.)
                    Toast.makeText(requireContext(), "Clipboard does not contain text", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Nothing to paste", Toast.LENGTH_SHORT).show()
            }
            true
        }
        speechButton.setOnClickListener {
            chatEditText.hideKeyboard()
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)  // NEW: Use launcher instead of ActivityCompat
            } else {
                startSpeechRecognition()
            }
        }

        clearButton.setOnClickListener {
            chatEditText.text.clear()
        }
    }

    private fun isTouchOutsideHeader(x: Float, y: Float): Boolean {
        val location = IntArray(2)
        headerContainer.getLocationOnScreen(location)
        val headerLeft = location[0].toFloat()
        val headerTop = location[1].toFloat()
        val headerRight = headerLeft + headerContainer.width
        val headerBottom = headerTop + headerContainer.height

        return x < headerLeft || x > headerRight || y < headerTop || y > headerBottom
    }
    private fun isTouchOnMenuButton(x: Float, y: Float): Boolean {
        val location = IntArray(2)
        menuButton.getLocationOnScreen(location)
        val buttonLeft = location[0].toFloat()
        val buttonTop = location[1].toFloat()
        val buttonRight = buttonLeft + menuButton.width
        val buttonBottom = buttonTop + menuButton.height

        // Return true if the touch is INSIDE the button's bounds
        return x >= buttonLeft && x <= buttonRight && y >= buttonTop && y <= buttonBottom
    }
    private fun updateInitialUI() {
        val isExtended = sharedPreferencesHelper.getExtPreference()
        val hasText = !chatEditText.text.isNullOrEmpty()
        convoButton.isSelected = sharedPreferencesHelper.getConversationModeEnabled()
        biometricButton.isSelected = sharedPreferencesHelper.getBiometricEnabled()
        extendButton.isSelected = isExtended
        utilityButton.visibility = if (isExtended) View.VISIBLE else View.GONE

        if (isExtended) {
            if (hasText) {
                clearButton.visibility = View.VISIBLE
                speechButton.visibility = View.GONE
            } else {
                clearButton.visibility = View.GONE
                speechButton.visibility = View.VISIBLE
            }
        } else {
            clearButton.visibility = View.GONE
            speechButton.visibility = View.GONE
        }
    }
    private fun speakText(text: String, position: Int) {
        if (isSpeaking) {
            if (position == currentSpeakingPosition) {
                // Stop current speech
                textToSpeech.stop()
                onSpeechFinished()
            } else {
                // Stop old and start new
                textToSpeech.stop()
                onSpeechFinished()
                // Start new
                isSpeaking = true
                currentSpeakingPosition = position
                chatAdapter.updateTtsState(isSpeaking, currentSpeakingPosition)
                // flashissue: Update icon directly if holder is attached, else notify
                updateIconDirectlyOrNotify(position, R.drawable.ic_stop_circle)
                val safeText = text.take(3900)
                if (safeText.length < text.length) {
                    Toast.makeText(requireContext(), "Text truncated for TTS (too long)", Toast.LENGTH_SHORT).show()
                }
                textToSpeech.speak(safeText, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
            }
        } else {
            // Start new
            isSpeaking = true
            currentSpeakingPosition = position
            chatAdapter.updateTtsState(isSpeaking, currentSpeakingPosition)
            // flashissue: Update icon directly if holder is attached, else notify
            updateIconDirectlyOrNotify(position, R.drawable.ic_stop_circle)
            val safeText = text.take(3900)
            if (safeText.length < text.length) {
                Toast.makeText(requireContext(), "Text truncated for TTS (too long)", Toast.LENGTH_SHORT).show()
            }
            textToSpeech.speak(safeText, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
        }
    }
    private fun updateIconDirectlyOrNotify(position: Int, @DrawableRes iconRes: Int) {
        val lm = chatRecyclerView.layoutManager as? LinearLayoutManager ?: return
        val vh = lm.findViewByHolder(position)          // extension below
        if (vh is ChatAdapter.AssistantViewHolder && vh.itemView.isAttachedToWindow) {
            vh.ttsButton.setImageResource(iconRes)      // fast path – no flash
        } else {
            chatAdapter.notifyItemChanged(position)     // slow path
        }
    }

    /* helper – returns the ViewHolder that is *currently* bound to the given adapter position */
    private fun LinearLayoutManager.findViewByHolder(pos: Int): RecyclerView.ViewHolder? {
        return findViewByPosition(pos)?.let { chatRecyclerView.getChildViewHolder(it) }
    }





    private fun onSpeechFinished() {
        isSpeaking = false
        val pos = currentSpeakingPosition
        currentSpeakingPosition = -1
        chatAdapter.updateTtsState(isSpeaking, currentSpeakingPosition)
        if (pos != -1) {
            // flashissue: Update icon directly if holder is attached, else notify
            updateIconDirectlyOrNotify(pos, R.drawable.ic_volume_up)
        }
    }
    private fun updateButtonVisibility() {
        // If both buttons are already gone (extended OFF), do nothing
        if (clearButton.isGone && speechButton.isGone) return

        val hasText = !chatEditText.text.isNullOrEmpty()
        if (hasText) {
            clearButton.visibility = View.VISIBLE
            speechButton.visibility = View.GONE
        } else {
            clearButton.visibility = View.GONE
            speechButton.visibility = View.VISIBLE
        }
    }
    private fun showMenu() {
        buttonsContainer.visibility = View.VISIBLE
        headerContainer.isVisible = true
        overlayView?.visibility = View.VISIBLE
    }

    private fun hideMenu() {
        buttonsContainer.visibility = View.GONE
        headerContainer.isVisible = false
        overlayView?.visibility = View.GONE
    }

    private fun showSaveChatDialogWithResultApi() {
        val dialog = SaveChatDialogFragment()
        childFragmentManager.setFragmentResultListener(SaveChatDialogFragment.REQUEST_KEY, viewLifecycleOwner) { key, bundle ->
            if (key == SaveChatDialogFragment.REQUEST_KEY) {
                val title = bundle.getString(SaveChatDialogFragment.BUNDLE_KEY_TITLE)
                if (!title.isNullOrBlank()) {
                    viewModel.saveCurrentChat(title)
                }
            }
        }
        dialog.show(childFragmentManager, SaveChatDialogFragment.TAG)
    }

    private fun formatModelName(modelString: String): String {
        return modelString.substringAfterLast("/")
            .substringBefore("@")
            .substringBefore(":")
    }

    // NEW: Separate setup for plusButton listener (with dialog options)
    private fun setupPlusButtonListener() {
        plusButton.setOnClickListener {
            val model = viewModel.activeChatModel.value
            if (model == null || !viewModel.isVisionModel(model)) {
                Toast.makeText(
                    requireContext(),
                    "Image selection not supported for the current model.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Load Image")
                .setItems(arrayOf("Take a Photo", "Choose from Gallery")) { _, which ->
                    when (which) {
                        0 -> { // Take Photo (Camera)
                            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                launchCamera()
                            }
                        }
                        1 -> { // Choose from Gallery
                            val allowedMimeTypes: Array<String> = when {
                                model.lowercase().contains("grok") -> {
                                    arrayOf("image/jpeg", "image/png")
                                }
                                else -> {
                                    arrayOf("image/jpeg", "image/png", "image/webp")
                                }
                            }
                            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "image/*"
                                putExtra(Intent.EXTRA_MIME_TYPES, allowedMimeTypes)
                            }
                            imagePicker.launch(intent)
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        // In setupPlusButtonListener(), after the existing setOnClickListener block
        plusButton.setOnLongClickListener {
            val model = viewModel.activeChatModel.value
            if (model == null || !viewModel.isVisionModel(model)) {
                Toast.makeText(requireContext(), "Image selection not supported for the current model.", Toast.LENGTH_SHORT).show()
                return@setOnLongClickListener false
            }

            // Direct camera launch on long-click
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                launchCamera()
            }
            true  // Consume long-click
        }

    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechLauncher.launch(intent)  // Use the launcher instead of startActivityForResult
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Speech recognition not supported", Toast.LENGTH_SHORT).show()
        }
    }
    /*private fun startForegroundService() {
        try {
            val serviceIntent = Intent(requireContext(), ForegroundService::class.java)
            requireContext().startService(serviceIntent)
        } catch (e: Exception) {
            //  Log.e("ChatFragment", "Failed to start foreground service", e)
        }
    }*/
    private fun startForegroundService() {
        try {
            val serviceIntent = Intent(requireContext(), ForegroundService::class.java)
            // Add the display name extra if needed (from previous response)
            val displayName = viewModel.getModelDisplayName(viewModel.activeChatModel.value ?: "Unknown Model")
            serviceIntent.putExtra("initial_title", displayName)
            requireContext().startService(serviceIntent)
        } catch (e: Exception) {
            Log.e("ChatFragment", "Failed to start foreground service", e)
        }
    }

    private fun stopForegroundService() {
        try {
            ForegroundService.stopService()
        } catch (e: Exception) {
            // Log.e("ChatFragment", "Failed to stop foreground service", e)
        }
    }
    private fun updateReasoningButtonAppearance() {
        // Use .value to get the current state from LiveData
        val isReasoningOn = viewModel.isReasoningEnabled.value ?: false
        val isAdvancedOn = viewModel.isAdvancedReasoningOn.value ?: false


        if (isReasoningOn && isAdvancedOn) {
            // STATE: Advanced Reasoning is ON. Add the outline.
            val strokeColor = ContextCompat.getColor(requireContext(), R.color.ora)
            val strokeWidth = resources.getDimensionPixelSize(R.dimen.advanced_reasoning_outline_width)

            reasoningButton.strokeColor = ColorStateList.valueOf(strokeColor)
            reasoningButton.strokeWidth = strokeWidth
        } else {
            // STATE: Normal or OFF. Remove the outline by setting its width to 0.
            reasoningButton.strokeWidth = 0
        }
    }
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {  // Fragment is now visible
            updateSystemMessageButtonState()
            chatEditText.requestFocus()
            viewModel.checkAdvancedReasoningStatus()
            viewModel._isNotiEnabled.value = sharedPreferencesHelper.getNotiPreference()
        }
    }

    override fun onResume() {
        super.onResume()
        updateSystemMessageButtonState()
        chatEditText.requestFocus()
        viewModel.checkAdvancedReasoningStatus()
        viewModel._isNotiEnabled.value = sharedPreferencesHelper.getNotiPreference()

        /*if (viewModel.isChatLoading.value == false) {
            if (viewModel.chatMessages.value.isNullOrEmpty()) {
                chatEditText.post {
                    chatEditText.showKeyboard()
                }
            } else {
                chatEditText.post {
                    chatEditText.hideKeyboard()
                }
            }
        }*/
    }


    fun onBackPressed(): Boolean {
        if (headerContainer.isVisible) {
            hideMenu()
            return true
        }
        return false
    }
    private fun launchCamera() {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "OpenChat_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
        }

        val uri = requireContext().contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { imageUri ->
            currentCameraUri = imageUri  // NEW: Store for reliable retrieval
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)  // Allow camera to write
            }
            if (cameraIntent.resolveActivity(requireContext().packageManager) != null) {
                cameraLauncher.launch(cameraIntent)
            } else {
                Toast.makeText(requireContext(), "No camera app available", Toast.LENGTH_SHORT).show()
                requireContext().contentResolver.delete(imageUri, null, null)
                currentCameraUri = null  // NEW: Clean up
            }
        } ?: run {
            Toast.makeText(requireContext(), "Could not create image entry", Toast.LENGTH_SHORT).show()
        }
    }
    fun startSpeechRecognitionSafely() {
        chatEditText.hideKeyboard()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)  // NEW: Use launcher
        } else {
            startSpeechRecognition()
        }
    }

}
