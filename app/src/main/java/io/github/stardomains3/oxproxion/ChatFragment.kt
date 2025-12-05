package io.github.stardomains3.oxproxion

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfRenderer
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PrintManager
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.style.BackgroundColorSpan
import android.text.util.Linkify
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
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
import io.noties.markwon.LinkResolverDef
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.SpanFactory
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableTheme
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import io.noties.markwon.movement.MovementMethodPlugin
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import io.noties.markwon.simple.ext.SimpleExtPlugin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.compareTo


class ChatFragment : Fragment(R.layout.fragment_chat) {
    private var isFontUpdate = false
    private var menuClosedByTouch = false
    private lateinit var speechLauncher: ActivityResultLauncher<Intent>
    private lateinit var textToSpeech: TextToSpeech
    private var isSpeaking = false
    private lateinit var chatFrameView: FrameLayout
    private var dimOverlay: View? = null
    private var currentSpeakingPosition = -1
    private lateinit var scrollersButton: MaterialButton
    private lateinit var helpButton: MaterialButton
    private lateinit var presetsButton: MaterialButton
    private lateinit var presetsButton2: MaterialButton
    private lateinit var attachmentButton: MaterialButton
    private var selectedImageBytes: ByteArray? = null
    private var selectedImageMime: String? = null
    private lateinit var plusButton: MaterialButton
    private lateinit var genButton: MaterialButton
    private lateinit var saveMarkdownFileButton: MaterialButton
    private lateinit var saveHtmlButton: MaterialButton
    private lateinit var printButton: MaterialButton
    private lateinit var biometricButton: MaterialButton
    private var originalSendIcon: Drawable? = null
    private lateinit var webSearchButton: MaterialButton
    private val viewModel: ChatViewModel by activityViewModels()
    private lateinit var modelNameTextView: TextView
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var chatEditText: EditText
    private lateinit var extBG: LinearLayout
    private lateinit var sendChatButton: MaterialButton
    private lateinit var resetChatButton: MaterialButton
    private lateinit var utilityButton: MaterialButton
    private lateinit var clearButton: MaterialButton
    private lateinit var speechButton: MaterialButton
    private lateinit var scrollToTopButton: MaterialButton
    private lateinit var scrollToBottomButton: MaterialButton
    private lateinit var extendButton: MaterialButton
    private lateinit var convoButton: MaterialButton
    private lateinit var saveChatButton: MaterialButton
    private lateinit var openSavedChatsButton: MaterialButton
    private lateinit var copyChatButton: MaterialButton
    private lateinit var buttonsRow2: LinearLayout
    private lateinit var menuButton: MaterialButton
    private lateinit var saveapiButton: MaterialButton
    private lateinit var lanButton: MaterialButton
    private lateinit var pdfChatButton: MaterialButton
    private lateinit var systemMessageButton: MaterialButton
    private lateinit var streamButton: MaterialButton
    private lateinit var reasoningButton: MaterialButton
    private var ttsAvailable = true
    private lateinit var setMaxTokensButton: MaterialButton
    private lateinit var notiButton: MaterialButton

    private lateinit var fontsButton: MaterialButton
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
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var pdfPicker: ActivityResultLauncher<Array<String>>
    private var currentTempImageFile: File? = null  // Tracks PDF PNG temp file
    data class AttachedFile(
        val fileName: String,
        val content: String,
        val size: Long
    )
    private var lastContentLength = 0
    private var hasScrolled = false
    private lateinit var textFilePicker: ActivityResultLauncher<String>
    private val pendingFiles = mutableListOf<AttachedFile>()
    private val MAX_FILE_SIZE = 3 * 1024 * 1024 // 3MB total
    private val MAX_SINGLE_FILE_SIZE = 1024 * 1024 // 1MB per file
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
                 //   Log.e("ChatFragment", "Error processing photo: ${e.message}", e)
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

                          //  Log.d("ChatFragment", "Persistent URI granted for gallery: $u")
                        } catch (e: SecurityException) {
                         //   Log.e("ChatFragment", "Persistent permission failed: ${e.message}", e)
                            Toast.makeText(requireContext(), "Image access limited; tap won't open full file", Toast.LENGTH_SHORT).show()
                            // Fallback: No Uri set, use base64 display only
                        }
                    }
                }
            }
        }
        pdfPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { processPdfUri(it) }  // Null-safe: Call if non-null
        }
        textFilePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isNotEmpty()) {
                // Process each URI (with size/MIME checks via processTextFile)
                var successCount = 0
                var errorCount = 0
                uris.forEach { uri ->
                    processTextFile(uri)  // Your updated function—handles one at a time
                    // Note: Since processTextFile is async, we don't await here; toasts/UI update inside it
                    // For batch feedback, you could collect results, but simple loop + toasts work fine
                }
                // Optional: Single toast after all (but since async, use a counter or LiveData)

                //  Toast.makeText(requireContext(), "${uris.size} files processed", Toast.LENGTH_SHORT).show()
            }
        }
        // --- Initialize Views from fragment_chat.xml ---
        pdfChatButton = view.findViewById(R.id.pdfChatButton)
        systemMessageButton = view.findViewById(R.id.systemMessageButton)
        streamButton = view.findViewById(R.id.streamButton)
        reasoningButton = view.findViewById(R.id.reasoningButton)
        notiButton = view.findViewById(R.id.notiButton)
        webSearchButton = view.findViewById(R.id.webSearchButton)
        fontsButton = view.findViewById(R.id.fontsButton)
        extendButton = view.findViewById(R.id.extendButton)
        scrollToBottomButton = view.findViewById(R.id.scrollToBottomButton)
        scrollToTopButton = view.findViewById(R.id.scrollToTopButton)
        scrollersButton = view.findViewById(R.id.scrollersButton)
        convoButton  = view.findViewById(R.id.convoButton)
        clearButton = view.findViewById(R.id.clearButton)
        utilityButton = view.findViewById(R.id.utilityButton)
        speechButton = view.findViewById(R.id.speechButton)
        chatRecyclerView = view.findViewById(R.id.chatRecyclerView)
        chatEditText = view.findViewById(R.id.chatEditText)
        sendChatButton = view.findViewById(R.id.sendChatButton)
        extBG = view.findViewById(R.id.extBG)
        originalSendIcon = sendChatButton.icon
        resetChatButton = view.findViewById(R.id.resetChatButton)
        saveChatButton = view.findViewById(R.id.saveChatButton)
        openSavedChatsButton = view.findViewById(R.id.openSavedChatsButton)
        copyChatButton = view.findViewById(R.id.copyChatButton)
        saveMarkdownFileButton  = view.findViewById(R.id.saveMarkdownFileButton)
        saveHtmlButton  = view.findViewById(R.id.saveHtmlButton)
        printButton =   view.findViewById(R.id.printButton)
        buttonsRow2 = view.findViewById(R.id.buttonsRow2)
        menuButton = view.findViewById(R.id.menuButton)
        chatFrameView = view.findViewById(R.id.chatFrameView)
        saveapiButton = view.findViewById(R.id.saveapiButton)
        attachmentButton = view.findViewById(R.id.attachmentButton)
        lanButton = view.findViewById(R.id.lanButton)
        setMaxTokensButton = view.findViewById(R.id.setMaxTokensButton)
        buttonsContainer = view.findViewById(R.id.buttonsContainer)
        modelNameTextView = view.findViewById(R.id.modelNameTextView)
        attachmentPreviewContainer = view.findViewById(R.id.attachmentPreviewContainer)
        previewImageView = view.findViewById(R.id.previewImageView)
        removeAttachmentButton = view.findViewById(R.id.removeAttachmentButton)
        headerContainer = view.findViewById(R.id.headerContainer)
        helpButton = view.findViewById(R.id.helpButton)
        presetsButton = view.findViewById(R.id.presetsButton)
        presetsButton2 = view.findViewById(R.id.presetsButton2)
        arguments?.getString("shared_text")?.let { sharedText ->
            setSharedText(sharedText)
            arguments?.remove("shared_text") // To prevent re-processing
        }
        textToSpeech = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId?.startsWith("TTS_SAVE_") == true) {
                            val parts = utteranceId.split("_")
                            if (parts.size == 4 && parts[0] == "TTS" && parts[1] == "SAVE") {
                                val timestamp = parts[2].toLongOrNull() ?: return
                                val position = parts[3].toIntOrNull() ?: return
                                val context = requireContext()
                                val tempFile = File(context.cacheDir, "temp_tts_${timestamp}.wav")
                                val fileName = "TTS_${timestamp}_msg${position}.wav"

                                // 🎯 COROUTINES: IO → Main (Structured, Cancellable, No Thread Leaks!)
                                lifecycleScope.launch(Dispatchers.IO) {  // 🔧 BACKGROUND I/O
                                    var success = false
                                    try {
                                        // 🔍 File ready (onDone guarantees!)
                                        if (!tempFile.exists() || tempFile.length() == 0L) {
                                            throw Exception("TTS file empty (0 bytes)")
                                        }

                                        // 🎯 MediaStore Downloads (NO PERMISSIONS)
                                        val contentValues = ContentValues().apply {
                                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                            put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                                        }

                                        val resolver = context.contentResolver
                                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                                            ?: throw Exception("Failed to create MediaStore URI")

                                        resolver.openOutputStream(uri)?.use { outputStream ->
                                            tempFile.inputStream().use { inputStream ->
                                                inputStream.copyTo(outputStream)
                                            }
                                        } ?: throw Exception("Failed to open OutputStream")

                                        // ✅ Complete
                                        contentValues.clear()
                                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                                        resolver.update(uri, contentValues, null, null)

                                        success = true
                                        //   Log.d("TTS", "✅ Coroutines MediaStore save: $fileName (${tempFile.length()} bytes)")

                                    } catch (e: Exception) {
                                        //    Log.e("TTS", "Coroutines save failed", e)
                                    } finally {
                                        // 🧹 Cleanup
                                        tempFile.delete()
                                    }

                                    // 🎯 MAIN THREAD TOAST (Auto-switched!)
                                    withContext(Dispatchers.Main) {
                                        val message = if (success) {
                                            "✅ Saved to Downloads: $fileName"
                                        } else {
                                            "❌ Save failed"
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                        else {
                            requireActivity().runOnUiThread { onSpeechFinished() }  // Run on main thread
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        if (utteranceId?.startsWith("TTS_SAVE_") == true) {
                            Toast.makeText(requireContext(), "❌ TTS synthesis error", Toast.LENGTH_SHORT).show()
                            //Log.e("TTS", "TTS error for: $utteranceId")
                        }
                        else {
                            requireActivity().runOnUiThread {
                                Toast.makeText(
                                    requireContext(),
                                    "TTS error: Check TTS settings or engine",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onSpeechFinished()
                            }
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
        val theme = Prism4jThemeDarkula.create()
        val syntaxHighlightPlugin = SyntaxHighlightPlugin.create(prism4j, theme)

// ✅ CRITICAL: Custom table theme FIRST
        val customTableTheme = TableTheme.buildWithDefaults(requireContext())
            .tableBorderColor(Color.LTGRAY)
            .tableBorderWidth(2)
            .tableCellPadding(8)  // Increased for better readability
            .tableHeaderRowBackgroundColor("#121314".toColorInt())
            .build()

        markwon = Markwon.builder(requireContext())
            // ✅ TablePlugin EARLY with custom theme
            .usePlugin(TablePlugin.create(customTableTheme))

            // Core plugins next
            .usePlugin(HtmlPlugin.create())
            .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES))
            .usePlugin(StrikethroughPlugin.create())

            // ✅ TaskList before syntax/images
            .usePlugin(TaskListPlugin.create(
                "#007541".toColorInt(),
                "#007541".toColorInt(),
                "#F8F8F8".toColorInt()
            ))

            .usePlugin(syntaxHighlightPlugin)

            // ✅ Images AFTER table/syntax
            .usePlugin(CoilImagesPlugin.create(requireContext()))

            // Movement method LAST (after table/images)
            .usePlugin(MovementMethodPlugin.create())

            // Custom plugins
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver(LinkResolverDef())
                }
            })
            .usePlugin(SimpleExtPlugin.create { plugin ->
                plugin.addExtension(2, '=', SpanFactory { _, _ ->
                    val typedValue = TypedValue()
                    requireContext().theme.resolveAttribute(
                        android.R.attr.textColorHighlight, typedValue, true
                    )
                    BackgroundColorSpan(typedValue.data)
                })
            })
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .codeTextColor(Color.LTGRAY)
                        .codeBackgroundColor(Color.argb(128, 0, 0, 0))
                        .codeBlockBackgroundColor(Color.argb(128, 0, 0, 0))
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
        setupTextFilePicker()
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
                        menuClosedByTouch = true
                        // Return false to pass the touch to the button underneath
                        return@setOnTouchListener false
                    }
                }
                // If touch is inside the header, let it pass through to the header
                false
            }
        }
        rootView.addView(overlayView)
        dimOverlay = view.findViewById<View>(R.id.dimOverlay)

        viewModel.activeChatModel.observe(viewLifecycleOwner) { model ->
            if (model != null) {
                modelNameTextView.text = viewModel.getModelDisplayName(model)
                if(viewModel.isVisionModel(model)){
                    plusButton.icon.alpha = 255
                    plusButton.isEnabled = true
                }
                else
                {
                    plusButton.icon.alpha = 102
                    plusButton.isEnabled = false
                }
                genButton.visibility = if (viewModel.isImageGenerationModel(model)) View.VISIBLE else View.GONE
                reasoningButton.visibility = if (viewModel.isReasoningModel(model)) View.VISIBLE else View.GONE
                // BUG FIX START: Clear staged image if new model is not a vision model
                if (selectedImageBytes != null && !viewModel.isVisionModel(model)) {
                    selectedImageBytes = null
                    selectedImageMime = null
                    attachmentPreviewContainer.visibility = View.GONE
                    Toast.makeText(requireContext(), "Image removed: selected model doesn't support images.", Toast.LENGTH_SHORT).show()
                }
                val isLanModel = viewModel.activeModelIsLan()
                if (isLanModel && viewModel.isWebSearchEnabled.value == true) {
                    // Auto-disable if it was ON (prevents sending invalid requests)
                    viewModel.toggleWebSearch()
                }

                webSearchButton.visibility = if (isLanModel) View.GONE else View.VISIBLE

            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sharedText.filterNotNull().collect { text ->
                    setSharedText(text)
                    viewModel.textConsumed()
                }
            }
        }

        viewModel.chatMessages.observe(viewLifecycleOwner) { messages ->
            chatAdapter.setMessages(messages)
            val hasMessages = messages.isNotEmpty()
            if(hasMessages){
                resetChatButton.icon.alpha = 255
                saveChatButton.icon.alpha = 255
                resetChatButton.isVisible = true
                val lastMessage = messages.last()
                if (lastMessage.role == "assistant" && lastMessage.content is JsonPrimitive) {
                    val contentStr = lastMessage.content.content
                    val currentLen = contentStr.length
                    if (contentStr == "working...") {
                        lastContentLength = 0
                        /*huh? chatRecyclerView.post {
                             chatRecyclerView.post {
                                 chatRecyclerView.post {
                                     val lastPos = chatAdapter.itemCount - 1
                                     val lastVh = chatRecyclerView.findViewHolderForAdapterPosition(lastPos)
                                     if (lastVh != null) {
                                         // Start with good position: leave some margin at top (-12px)
                                         layoutManager.scrollToPositionWithOffset(lastPos, -12)
                                     } else {
                                         layoutManager.scrollToPositionWithOffset(lastPos, -1000000)
                                     }
                                 }
                             }
                         }*/
                        chatRecyclerView.post {
                            chatRecyclerView.post {
                                val lastPos = chatAdapter.itemCount - 1
                                val lastVh = chatRecyclerView.findViewHolderForAdapterPosition(lastPos)
                                if (lastVh != null) {
                                    // Exact: Bottom-align (RV height - item height)
                                    val offset = -(chatRecyclerView.height - lastVh.itemView.height)
                                    layoutManager.scrollToPositionWithOffset(lastPos, offset)
                                } else {
                                    // Fallback: smooth scroll
                                    layoutManager.scrollToPositionWithOffset(lastPos, -1000000)
                                }
                            }
                        }
                    }  else if (currentLen > lastContentLength && !hasScrolled) {
                        lastContentLength = currentLen
                        chatRecyclerView.post {
                            chatRecyclerView.post {
                                chatRecyclerView.post {  // Triple post handles layout/draw timing
                                    val lastPos = chatAdapter.itemCount - 1
                                    val lastVh = chatRecyclerView.findViewHolderForAdapterPosition(lastPos)

                                    if (lastVh != null) {
                                        val bubbleTop = lastVh.itemView.top

                                        // Fix the jitter: use more conservative threshold
                                        // Only stop when we're really getting close to going off-screen
                                        if (bubbleTop >= -8) {
                                            // Still OK → keep auto-scrolling with fixed offset
                                            layoutManager.scrollToPositionWithOffset(lastPos, -12)
                                        } else {
                                            // Too high! Stop here and lock position
                                            hasScrolled = true
                                            viewModel.setUserScrolledDuringStream(true)
                                            layoutManager.scrollToPositionWithOffset(lastPos, -12)
                                        }

                                    } else {
                                        // Fallback until view is ready
                                        chatRecyclerView.smoothScrollToPosition(lastPos)
                                    }
                                }
                            }
                        }
                    } else {
                        lastContentLength = currentLen
                    }
                }
            }
            else
            {
                resetChatButton.icon.alpha = 102
                saveChatButton.icon.alpha = 102
            }
            if(sharedPreferencesHelper.getScrollersPreference()){
                chatRecyclerView.post {
                    val canScrollUp = chatRecyclerView.canScrollVertically(-1)
                    val canScrollDown = chatRecyclerView.canScrollVertically(1)
                    scrollToTopButton.visibility = if (canScrollUp) View.VISIBLE else View.INVISIBLE
                    scrollToBottomButton.visibility = if (canScrollDown) View.VISIBLE else View.INVISIBLE
                }
            }
            saveChatButton.isEnabled = hasMessages
            resetChatButton.isEnabled = hasMessages
           // pdfChatButton.isVisible = hasMessages
            //copyChatButton.isVisible = hasMessages
            buttonsRow2.isVisible = hasMessages
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
                        viewModel.getMessageText(lastMessage.content) != "working...") {
                        chatRecyclerView.post {
                            val position = messages.size - 1
                            val holder = chatRecyclerView.findViewHolderForAdapterPosition(position) as? ChatAdapter.AssistantViewHolder
                            holder?.ttsButton?.performClick()
                        }
                    }
                }
            }
            if (!isAwaiting && sharedPreferencesHelper.getStreamingPreference()) {
                chatAdapter.finalizeStreaming()
            }
            sendChatButton.isEnabled = true
            val materialButton = sendChatButton
            if (isAwaiting) {
                materialButton.setIconResource(R.drawable.ic_stop)
            } else {
                materialButton.icon = originalSendIcon
                val messages = viewModel.chatMessages.value
                if (messages?.isNotEmpty() == true) {
                    val lastMessage = messages.last()
                    if (lastMessage.role == "assistant") {
                        val originalColor = modelNameTextView.currentTextColor
                        val isError = lastMessage.content
                            .let { it as? JsonPrimitive }?.content?.startsWith("**Error:**") == true
                        val targetColor = if (isError) "#8c1911".toColorInt()
                        else          "#222f3d".toColorInt()
                        modelNameTextView.animateColor(originalColor, targetColor,   1000)
                        modelNameTextView.animateColor(targetColor,   originalColor, 3000)
                    }
                }
            }

            if (!isAwaiting && viewModel.shouldAutoOffWebSearch()) {
                viewModel.resetWebSearchAutoOff()
                if (viewModel.isWebSearchEnabled.value == true) {
                    viewModel.toggleWebSearch()  // Turn it off
                    Toast.makeText(requireContext(), "Web search auto-disabled (one-time use)", Toast.LENGTH_SHORT).show()
                }
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

        viewModel.isWebSearchEnabled.observe(viewLifecycleOwner) { isEnabled ->
            webSearchButton.isSelected = isEnabled
        }

        viewModel.isScrollersEnabled.observe(viewLifecycleOwner) { isEnabled ->
            scrollersButton.isSelected = isEnabled
            if(isEnabled) {
                chatRecyclerView.post {
                    val canScrollUp = chatRecyclerView.canScrollVertically(-1)
                    val canScrollDown = chatRecyclerView.canScrollVertically(1)
                    scrollToTopButton.visibility = if (canScrollUp) View.VISIBLE else View.INVISIBLE
                    scrollToBottomButton.visibility = if (canScrollDown) View.VISIBLE else View.INVISIBLE
                }
            }
            else{
                scrollToTopButton.visibility = View.INVISIBLE
                scrollToBottomButton.visibility = View.INVISIBLE
            }
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
         /*   if(isEnabled && !ForegroundService.isRunningForeground ){
                startForegroundService()
            }
            else if (!isEnabled && ForegroundService.isRunningForeground){
                stopForegroundService()
            }*/
        }
        viewModel.toolUiEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
        viewModel.presetAppliedEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                updateSystemMessageButtonState()
                convoButton.isSelected = sharedPreferencesHelper.getConversationModeEnabled()
            }
        }
        /*viewModel.scrollToBottomEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                if (chatAdapter.itemCount > 0) {
                    chatRecyclerView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            chatRecyclerView.viewTreeObserver.removeOnPreDrawListener(this)
                            val position = chatAdapter.itemCount - 1
                            layoutManager.scrollToPositionWithOffset(position, -12)
                            return true
                        }
                    })
                }
            }
        }*/
        viewModel.scrollToBottomEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                chatRecyclerView.post {
                    val position = chatAdapter.itemCount - 1
                    if (position >= 0) {
                        hasScrolled = false
                        layoutManager.scrollToPositionWithOffset(position, -12)
                        chatRecyclerView.post {
                            if (sharedPreferencesHelper.getScrollersPreference()) {
                                val canScrollUp = chatRecyclerView.canScrollVertically(-1)
                                val canScrollDown = chatRecyclerView.canScrollVertically(1)
                                scrollToTopButton.visibility =
                                    if (canScrollUp) View.VISIBLE else View.INVISIBLE
                                scrollToBottomButton.visibility =
                                    if (canScrollDown) View.VISIBLE else View.INVISIBLE
                            }
                        }
                    }
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
        val selectedFontName = sharedPreferencesHelper.getSelectedFont()
        val typeface = try {
            when (selectedFontName) {
                "system_default" -> Typeface.DEFAULT
                "alansans_regular" -> ResourcesCompat.getFont(requireContext(), R.font.alansans_regular)
                "notoserif_regular" -> ResourcesCompat.getFont(requireContext(), R.font.notoserif_regular)
                "alexandria_regular" -> ResourcesCompat.getFont(requireContext(), R.font.alexandria_regular)
                "aronesans_regular" -> ResourcesCompat.getFont(requireContext(), R.font.aronesans_regular)
                "funneldisplay_regular" -> ResourcesCompat.getFont(requireContext(), R.font.funneldisplay_regular)
                "geologica_light" -> ResourcesCompat.getFont(requireContext(), R.font.geologica_light)
                "googlesansflex_regular" -> ResourcesCompat.getFont(requireContext(), R.font.googlesansflex_regular)
                "instrumentsans_regular" -> ResourcesCompat.getFont(requireContext(), R.font.instrumentsans_regular)
                "lexend_regular" -> ResourcesCompat.getFont(requireContext(), R.font.lexend_regular)
                "merriweather_24pt_regular" -> ResourcesCompat.getFont(requireContext(), R.font.merriweather_24pt_regular)
                "merriweathersans_light" -> ResourcesCompat.getFont(requireContext(), R.font.merriweathersans_light)
                "mplus2_regular" -> ResourcesCompat.getFont(requireContext(), R.font.mplus2_regular)
                "nokora_regular" -> ResourcesCompat.getFont(requireContext(), R.font.nokora_regular)
                "notosans_regular" -> ResourcesCompat.getFont(requireContext(), R.font.notosans_regular)
                "opensans_regular" -> ResourcesCompat.getFont(requireContext(), R.font.opensans_regular)
                "outfit_regular" -> ResourcesCompat.getFont(requireContext(), R.font.outfit_regular)
                "poppins_regular" -> ResourcesCompat.getFont(requireContext(), R.font.poppins_regular)
                "readexpro_regular" -> ResourcesCompat.getFont(requireContext(), R.font.readexpro_regular)
                "roboto_regular" -> ResourcesCompat.getFont(requireContext(), R.font.roboto_regular)
                "robotoserif_regular" -> ResourcesCompat.getFont(requireContext(), R.font.robotoserif_regular)
                "sourceserif4_regular" -> ResourcesCompat.getFont(requireContext(), R.font.sourceserif4_regular)
                "tasaorbiter_regular" -> ResourcesCompat.getFont(requireContext(), R.font.tasaorbiter_regular)
                "ubuntusans_regular" -> ResourcesCompat.getFont(requireContext(), R.font.ubuntusans_regular)
                "vendsans_regular" -> ResourcesCompat.getFont(requireContext(), R.font.vendsans_regular)
                else -> ResourcesCompat.getFont(requireContext(), R.font.geologica_light)
            }
        } catch (e: Exception) {
            Typeface.DEFAULT  // Fallback if any font load fails
        }
        chatEditText.typeface = typeface ?: Typeface.DEFAULT
        modelNameTextView.typeface = typeface ?: Typeface.DEFAULT
        chatAdapter.updateFont(typeface)
        val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (notificationManager != null) {
            val channels = notificationManager.notificationChannels
            val channelIds = channels.map { it.id }
            if (channelIds.contains("SilentUpdatesChannel")) {
                notificationManager.deleteNotificationChannel("SilentUpdatesChannel")
            }
        }


        // end onviewcreated
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

    private fun setSharedText(sharedText: String) {
        if (sharedText.isBlank()) {
            chatEditText.setText("")  // Handle empty case
            return
        }

        // Just set the raw text (trimmed for leading/trailing spaces)
        chatEditText.setText(sharedText.trim())
        chatEditText.setSelection(chatEditText.length())
    }

    private fun setupRecyclerView() {
        layoutManager = NonScrollingOnFocusLayoutManager(requireContext()).apply {
            stackFromEnd = false
        }
        chatAdapter = ChatAdapter(
            viewLifecycleOwner.lifecycleScope,
            markwon,
           // viewModel,
            { text, position -> speakText(text, position) },
            { text, position -> synthesizeToWavFile(text, position) },
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
                    .setPositiveButton("Resend") { _, _ ->
                        // NEW: Truncate only AFTER position (keep original user message)
                        viewModel.truncateHistory(position + 1)

                        // NEW: Get system message (as before)
                        val systemMessage = sharedPreferencesHelper.getSelectedSystemMessage().prompt

                        // NEW: Use specialized resend (keeps original UI, sends content)
                        viewModel.resendExistingPrompt(position, systemMessage)

                       /* if (ForegroundService.isRunningForeground && sharedPreferencesHelper.getNotiPreference()) {
                            val apiIdentifier = viewModel.activeChatModel.value ?: "Unknown Model"
                            val displayName = viewModel.getModelDisplayName(apiIdentifier)
                            ForegroundService.updateNotificationStatusSilently(displayName, "Prompt resent. Awaiting Response.")
                        }*/
                        // UI polish: Hide menu, scroll to bottom (after resend starts)
                        hideMenu()
                        // Scroll to the kept user message + new thinking
                        chatRecyclerView.post {
                            if (chatAdapter.itemCount > 0) {
                                layoutManager.scrollToPosition(chatAdapter.itemCount - 1)
                            }
                        }
                    }
                    .setCancelable(true)
                    .show()
            },
            onDeleteMessage = { position ->
                // NEW: Confirmation dialog (similar to edit/redo)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete this message?")
                    .setMessage("This will remove the message and all following responses from the chat. This action cannot be undone.\n\nProceed?")
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setPositiveButton("Delete") { _, _ ->
                        // Call ViewModel to delete (removes message + after)
                        viewModel.deleteMessageAt(position)

                        // UI polish: Hide menu (if open), optional scroll to bottom
                        hideMenu()
                        chatRecyclerView.post {
                            if (chatAdapter.itemCount > 0) {
                                layoutManager.scrollToPosition(chatAdapter.itemCount - 1)
                            }
                        }

                        // Optional: Notification update
                        /*if (ForegroundService.isRunningForeground && sharedPreferencesHelper.getNotiPreference()) {
                            val apiIdentifier = viewModel.activeChatModel.value ?: "Unknown Model"
                            val displayName = viewModel.getModelDisplayName(apiIdentifier)
                            ForegroundService.updateNotificationStatusSilently(displayName, "Message deleted.")
                        }*/
                    }
                    .setCancelable(true)
                    .show()
            },

            onSaveMarkdown = { position, rawMarkdown ->
                viewModel.saveMarkdownToDownloads(rawMarkdown)  // Your ViewModel method
            },
            onCaptureItemToPng = ::captureItemToPng

        )
        chatRecyclerView.apply {
            adapter = chatAdapter
            layoutManager = this@ChatFragment.layoutManager
        }
        chatRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if ((newState == RecyclerView.SCROLL_STATE_DRAGGING&&!hasScrolled) && viewModel.isAwaitingResponse.value == true) {
                    hasScrolled = true
                    viewModel.setUserScrolledDuringStream(true)
                }
            }
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if(sharedPreferencesHelper.getScrollersPreference())
                    chatRecyclerView.post {  // Keep post for layout safety
                        val canScrollUp = chatRecyclerView.canScrollVertically(-1)
                        val canScrollDown = chatRecyclerView.canScrollVertically(1)
                        scrollToTopButton.visibility = if (canScrollUp) View.VISIBLE else View.INVISIBLE
                        scrollToBottomButton.visibility = if (canScrollDown) View.VISIBLE else View.INVISIBLE
                    }
            }
        })
        chatRecyclerView.setOnTouchListener { _, event ->
            if (viewModel.isAwaitingResponse.value == true &&
                (event.actionMasked == MotionEvent.ACTION_DOWN&&!hasScrolled)) {  // ACTION_DOWN catches tap/scroll start reliably
                hasScrolled = true
                viewModel.setUserScrolledDuringStream(true)

                // If you use this flag locally too!
            }

            false  // Pass through touch so RecyclerView still works normally
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
        if (!isFontUpdate) {  // Only stop the service if not a font update (i.e., actual app closure)
            stopForegroundService()
        }

    }
    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        removeAttachmentButton.setOnClickListener {
            selectedImageBytes = null
            selectedImageMime = null
            attachmentPreviewContainer.visibility = View.GONE
            viewModel.setPendingUserImageUri(null)
            previewImageView.setImageBitmap(null)
            currentTempImageFile?.delete()
            currentTempImageFile = null
            Toast.makeText(requireContext(), "Attachment removed", Toast.LENGTH_SHORT).show()
        }
        webSearchButton.setOnClickListener {
            //  hideMenu()
            viewModel.toggleWebSearch()
            // NEW: Set flag to auto-disable after next response
            viewModel.setWebSearchAutoOff(true)
        }
        webSearchButton.setOnLongClickListener {
            showWebSearchEngineDialog()
            true
        }
        sendChatButton.setOnClickListener {
            if (viewModel.isAwaitingResponse.value == true) {
                hasScrolled = false
                viewModel.cancelCurrentRequest()
                //   viewModel.playCancelTone()
            } else {
                // --- API Key Check ---
                if (viewModel.activeModelIsLan()) {
                    // LAN model: Check endpoint instead of API key
                    val lanEndpoint = viewModel.getLanEndpoint()
                    if (lanEndpoint.isNullOrBlank()) {
                        Toast.makeText(requireContext(), "LAN endpoint is not configured.", Toast.LENGTH_SHORT)
                            .show()
                        return@setOnClickListener
                    }
                } else {
                    // Non-LAN model: Check API key
                    if (viewModel.activeChatApiKey.isBlank()) {
                        Toast.makeText(requireContext(), "API Key is not set.", Toast.LENGTH_SHORT)
                            .show()
                        return@setOnClickListener
                    }
                }
                hasScrolled = false
                var prompt = chatEditText.text.toString().trim()
                if (pendingFiles.isNotEmpty()) {
                    val fileSections = pendingFiles.mapIndexed { index, file ->  // Explicit -> String
                        val cleanContent = file.content.trim()
                        if (cleanContent.isNotBlank()) {
                            // Raw string for if branch: No escaping needed, newline after filename
                            """File ${index + 1} (${file.fileName}):

```text
$cleanContent
```"""
                        } else {
                            // Raw string for else branch: Matches type, no escaping
                            """File ${index + 1} (${file.fileName}): (empty file)"""
                        }
                    }.joinToString("\n\n")  // Now safely String

                    // Reassign prompt: Type-safe since fileSections is String
                    prompt = if (prompt.isNotBlank()) {
                        "$fileSections\n\n**User message:**\n$prompt"
                    } else {
                        "$fileSections\n\nPlease analyze these attached files."
                    }
                }
                if (prompt.isNotBlank() || selectedImageBytes != null) {
                    /* if (!ForegroundService.isRunningForeground) {
                         ChatServiceGate.shouldRunService = true
                         startForegroundService()
                     }*/

                  /*  if (ForegroundService.isRunningForeground && sharedPreferencesHelper.getNotiPreference()) {
                        val apiIdentifier = viewModel.activeChatModel.value ?: "Unknown Model"
                        val displayName = viewModel.getModelDisplayName(apiIdentifier)
                        ForegroundService.updateNotificationStatusSilently(displayName, "Prompt sent. Awaiting Response.")
                    }*/

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
                    pendingFiles.clear()
                    updateAttachmentButton()
                }
            }
        }
        genButton.setOnClickListener {
            val model = viewModel.activeChatModel.value
            if (model !in listOf("google/gemini-2.5-flash-image", "google/gemini-2.5-flash-image-preview","google/gemini-3-pro-image-preview")) {
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
            chatEditText.hideKeyboard()
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
                        /*if (ForegroundService.isRunningForeground && sharedPreferencesHelper.getNotiPreference()) {
                            val apiIdentifier = viewModel.activeChatModel.value ?: "Unknown Model"
                            val displayName = viewModel.getModelDisplayName(apiIdentifier)
                            ForegroundService.updateNotificationStatusSilently(displayName, "Model Changed")
                        }*/
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
            chatEditText.hideKeyboard()
            parentFragmentManager.beginTransaction()
                .hide(this)
                .add(R.id.fragment_container, SystemMessageLibraryFragment())
                .addToBackStack(null)
                .commit()
        }
        resetChatButton.setOnLongClickListener {
           /* if (ForegroundService.isRunningForeground && sharedPreferencesHelper.getNotiPreference()) {
                val apiIdentifier = viewModel.activeChatModel.value ?: "Unknown Model"
                val displayName = viewModel.getModelDisplayName(apiIdentifier)
                ForegroundService.updateNotificationStatusSilently(displayName, "oxproxion is Ready.")
            }*/
            viewModel.startNewChat()
            currentTempImageFile?.delete()
            currentTempImageFile = null
            previewImageView.setImageBitmap(null)
            // Add to reset logic
            pendingFiles.clear()
            updateAttachmentButton()
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
                   /* if (ForegroundService.isRunningForeground && sharedPreferencesHelper.getNotiPreference()) {
                        val apiIdentifier = viewModel.activeChatModel.value ?: "Unknown Model"
                        val displayName = viewModel.getModelDisplayName(apiIdentifier)
                        ForegroundService.updateNotificationStatusSilently(displayName, "oxproxion is Ready.")
                    }*/
                    /*if (ForegroundService.isRunningForeground) {
                        stopForegroundService()
                    }
                    else{
                        ChatServiceGate.shouldRunService = false
                    }*/
                    viewModel.startNewChat()
                    currentTempImageFile?.delete()
                    currentTempImageFile = null
                    previewImageView.setImageBitmap(null)
                    pendingFiles.clear()
                    updateAttachmentButton()
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
            chatEditText.hideKeyboard()
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
                      //  Log.e("ChatFragment", "PDF generation failed", e)
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

        copyChatButton.setOnLongClickListener {
            val chatText = viewModel.getFormattedChatHistory()  // Raw Markdown
            if (chatText.isNotBlank()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Chat History (Markdown)", chatText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Chat copied as Markdown!", Toast.LENGTH_SHORT).show()
                true  // Consume the long press
            } else {
                Toast.makeText(requireContext(), "Nothing to Copy", Toast.LENGTH_SHORT).show()
                true
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
        printButton.setOnClickListener {
            hideMenu()
            lifecycleScope.launch {
                val chatHtml = viewModel.getFormattedChatHistoryStyledHtml()
                if (chatHtml.isNotBlank()) {
                    printChatHtml(chatHtml)
                } else {
                    Toast.makeText(requireContext(), "Nothing to print", Toast.LENGTH_SHORT).show()
                }
            }
        }

        saveMarkdownFileButton.setOnClickListener {
            hideMenu()
            val chatText = viewModel.getFormattedChatHistoryMarkdownandPrint()
            if (chatText.isNotBlank()) {
                viewModel.saveMarkdownToDownloads(chatText)
                // No need for local Toast - ViewModel handles UI event via _toolUiEvent
            } else {
                Toast.makeText(requireContext(), "Nothing to save", Toast.LENGTH_SHORT).show()
            }
        }
        saveMarkdownFileButton.setOnLongClickListener {
            val chatText = viewModel.getFormattedChatHistory()  // Raw Markdown
            if (chatText.isNotBlank()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Chat History (Markdown)", chatText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Chat copied as Markdown!", Toast.LENGTH_SHORT).show()
                true  // Consume the long press
            } else {
                Toast.makeText(requireContext(), "Nothing to Copy", Toast.LENGTH_SHORT).show()
                true
            }
        }
        saveHtmlButton.setOnClickListener {
            hideMenu()
            lifecycleScope.launch {
                val innerHtml = viewModel.getFormattedChatHistoryStyledHtml()
                if (innerHtml.isNotBlank()) {
                    viewModel.saveHtmlToDownloads(innerHtml)
                    // VM handles success Toast via _toolUiEvent
                } else {
                    Toast.makeText(requireContext(), "Nothing to save", Toast.LENGTH_SHORT).show()
                }
            }
        }


        menuButton.setOnClickListener {
            if (headerContainer.isVisible) {
                hideMenu()
            } else {
                chatEditText.hideKeyboard()
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
        presetsButton.setOnClickListener {
            hideMenu()
            parentFragmentManager.beginTransaction()
                .hide(this)
                .add(R.id.fragment_container, PresetsListFragment())
                .addToBackStack(null)
                .commit()
        }
        presetsButton2.setOnClickListener {
            chatEditText.hideKeyboard()
            hideMenu()
            parentFragmentManager.beginTransaction()
                .hide(this)
                .add(R.id.fragment_container, PresetsListFragment())
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
        lanButton.setOnClickListener {
            hideMenu()
            SaveLANDialogFragment().show(childFragmentManager, SaveLANDialogFragment.TAG)
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
        fontsButton.setOnClickListener {
            hideMenu()

            // Define font options (display name, font res ID or null for system default, style res ID)
            val fontOptions = listOf(
                Triple("System Default", null, R.style.Base_Theme_Oxproxion),
                Triple("Alan Sans Regular", R.font.alansans_regular, R.style.Font_AlanSansRegular),
                Triple("Alexandria Regular", R.font.alexandria_regular, R.style.Font_AlexandriaRegular),
                Triple("Arone Sans Regular", R.font.aronesans_regular, R.style.Font_AroneSansRegular),
                Triple("Funnel Display Regular", R.font.funneldisplay_regular, R.style.Font_FunnelDisplayRegular),
                Triple("Geologica Light", R.font.geologica_light, R.style.Font_GeologicaLight),
                Triple("Google Sans Flex Regular", R.font.googlesansflex_regular, R.style.Font_GoogleSansFlexRegular),
                Triple("Instrument Sans Regular", R.font.instrumentsans_regular, R.style.Font_InstrumentSansRegular),
                Triple("Lexend Regular", R.font.lexend_regular, R.style.Font_LexendRegular),
                Triple("Merriweather 24pt Regular", R.font.merriweather_24pt_regular, R.style.Font_Merriweather24ptRegular),
                Triple("Merriweather Sans Light", R.font.merriweathersans_light, R.style.Font_MerriweathersansLight),
                Triple("M Plus 2 Regular", R.font.mplus2_regular, R.style.Font_MPlus2Regular),
                Triple("Nokora Regular", R.font.nokora_regular, R.style.Font_NokoraRegular),
                Triple("Noto Sans Regular", R.font.notosans_regular, R.style.Font_NotoSansRegular),
                Triple("Noto Serif", R.font.notoserif_regular, R.style.Font_NotoSerifRegular),
                Triple("Open Sans Regular", R.font.opensans_regular, R.style.Font_OpenSansRegular),
                Triple("Outfit Regular", R.font.outfit_regular, R.style.Font_OutfitRegular),
                Triple("Poppins Regular", R.font.poppins_regular, R.style.Font_PoppinsRegular),
                Triple("Readex Pro Regular", R.font.readexpro_regular, R.style.Font_ReadexProRegular),
                Triple("Roboto Regular", R.font.roboto_regular, R.style.Font_RobotoRegular),
                Triple("Roboto Serif Regular", R.font.robotoserif_regular, R.style.Font_RobotoSerifRegular),
                Triple("Source Serif 4 Regular", R.font.sourceserif4_regular, R.style.Font_SourceSerif4Regular),
                Triple("Tasa Orbiter Regular", R.font.tasaorbiter_regular, R.style.Font_TasaOrbiterRegular),
                Triple("Ubuntu Sans Regular", R.font.ubuntusans_regular, R.style.Font_UbuntuSansRegular),
                Triple("Vend Sans Regular", R.font.vendsans_regular, R.style.Font_VendSansRegular)
            )

            // Helper function: Maps fontResId to its string name (or "system_default" for null)
            fun getFontNameFromRes(fontResId: Int?): String = when (fontResId) {
                null -> "system_default"
                R.font.alansans_regular -> "alansans_regular"
                R.font.alexandria_regular -> "alexandria_regular"
                R.font.notoserif_regular -> "notoserif_regular"
                R.font.aronesans_regular -> "aronesans_regular"
                R.font.funneldisplay_regular -> "funneldisplay_regular"
                R.font.geologica_light -> "geologica_light"
                R.font.googlesansflex_regular -> "googlesansflex_regular"
                R.font.instrumentsans_regular -> "instrumentsans_regular"
                R.font.lexend_regular -> "lexend_regular"
                R.font.merriweather_24pt_regular -> "merriweather_24pt_regular"
                R.font.merriweathersans_light -> "merriweathersans_light"
                R.font.mplus2_regular -> "mplus2_regular"
                R.font.nokora_regular -> "nokora_regular"
                R.font.notosans_regular -> "notosans_regular"
                R.font.opensans_regular -> "opensans_regular"
                R.font.outfit_regular -> "outfit_regular"
                R.font.poppins_regular -> "poppins_regular"
                R.font.readexpro_regular -> "readexpro_regular"
                R.font.roboto_regular -> "roboto_regular"
                R.font.robotoserif_regular -> "robotoserif_regular"
                R.font.sourceserif4_regular -> "sourceserif4_regular"
                R.font.tasaorbiter_regular -> "tasaorbiter_regular"
                R.font.ubuntusans_regular -> "ubuntusans_regular"
                R.font.vendsans_regular -> "vendsans_regular"
                else -> "geologica_light"  // Fallback
            }

            // Create the dialog first (to make it accessible in the adapter)
            val dialog = MaterialAlertDialogBuilder(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered)
                .setTitle("Select Font")
                .setNegativeButton("Cancel", null)
                .create()

            // Create a RecyclerView adapter for font previews
            val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val textView = TextView(parent.context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        setPadding(32, 16, 32, 16)  // Padding for touch targets
                        textSize = 18f
                        gravity = Gravity.CENTER
                        isClickable = true
                    }
                    return object : RecyclerView.ViewHolder(textView) {}
                }

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val (displayName, fontResId, styleResId) = fontOptions[position]
                    val textView = holder.itemView as TextView
                    textView.text = displayName
                    // Apply the font for preview (use system default if fontResId is null)
                    textView.typeface = if (fontResId != null) {
                        try {
                            ResourcesCompat.getFont(textView.context, fontResId)
                        } catch (e: Exception) {
                            ResourcesCompat.getFont(textView.context, R.font.geologica_light)  // Fallback
                        }
                    } else {
                        Typeface.DEFAULT  // System default font
                    }

                    // Highlight current font in #a0610a (using helper for isSelected)
                    val currentFont = sharedPreferencesHelper.getSelectedFont()
                    val fontName = getFontNameFromRes(fontResId)
                    val isSelected = fontName == currentFont
                    if (isSelected) {
                        textView.setTextColor("#a0610a".toColorInt())  // High contrast for readability
                    } else {
                        textView.setTextColor(Color.WHITE)  // Default text color
                    }

                    // On tap: Save, apply, dismiss (using helper for fontName)
                    textView.setOnClickListener {
                        val fontName = getFontNameFromRes(fontResId)
                        sharedPreferencesHelper.saveSelectedFont(fontName)

                        // Manual font update for static views (e.g., chatEditText)
                        val newTypeface = if (fontResId != null) {
                            try {
                                ResourcesCompat.getFont(requireContext(), fontResId)
                            } catch (e: Exception) {
                                ResourcesCompat.getFont(requireContext(), R.font.geologica_light)
                            }
                        } else {
                            Typeface.DEFAULT  // System default
                        }
                        // Apply to your static TextViews (add more as needed)
                        chatEditText.typeface = newTypeface
                        modelNameTextView.typeface = newTypeface
                        chatAdapter.updateFont(newTypeface)

                        dialog.dismiss()
                    }
                }

                override fun getItemCount() = fontOptions.size
            }

            // Set up RecyclerView and show dialog
            val recyclerView = RecyclerView(requireContext()).apply {
                layoutManager = LinearLayoutManager(requireContext())
                this.adapter = adapter
            }
            dialog.window?.setDimAmount(0.8f)

            dialog.setView(recyclerView)
            dialog.show()

            // Force underline the title after showing
            val titleView = dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)
            titleView?.paintFlags = titleView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        }
        convoButton.setOnClickListener {
            val currentState = sharedPreferencesHelper.getConversationModeEnabled()
            val newState = !currentState
            sharedPreferencesHelper.saveConversationModeEnabled(newState)
            convoButton.isSelected = newState
        }
        extendButton.setOnLongClickListener {
            val currentState = sharedPreferencesHelper.getExtPreference2()
            val newState = !currentState
            sharedPreferencesHelper.saveExtPreference2(newState)
           // extBG.visibility = if (newState) View.VISIBLE else View.GONE
            presetsButton2.visibility = if (newState) View.VISIBLE else View.GONE
            presetsButton.visibility = if (presetsButton2.isVisible) View.GONE else View.VISIBLE
            true
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

        scrollersButton.setOnClickListener {
            viewModel.toggleScrollers()
        }
        sendChatButton.setOnLongClickListener {
            val lastPos = chatAdapter.itemCount - 1
            if (lastPos >= 0) {
                layoutManager.scrollToPositionWithOffset(lastPos, -12)
            }
            else{
                showMenu()
            }
            true
        }

        scrollToTopButton.setOnClickListener {
            chatRecyclerView.post {
                chatRecyclerView.scrollToPosition(0)
                updateScrollButtonsVisibility()
            }
        }

        scrollToBottomButton.setOnClickListener {
            chatRecyclerView.post {
                val layoutManager = chatRecyclerView.layoutManager as LinearLayoutManager
                val lastIndex = chatAdapter.itemCount - 1
                if (lastIndex >= 0) {
                    layoutManager.scrollToPositionWithOffset(lastIndex, -1000000)  // Bottom-align
                }
                updateScrollButtonsVisibility()
            }
        }

        scrollToTopButton.setOnLongClickListener {
            scrollToPreviousScreen()
            true  // Consume long press
        }

        scrollToBottomButton.setOnLongClickListener {
            scrollToNextScreen()
            true  // Consume long press
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
        if (sharedPreferencesHelper.getExtPreference2()){
           // extBG.visibility = View.VISIBLE
            presetsButton2.visibility = View.VISIBLE
            presetsButton.visibility = View.GONE
        }
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

    // 🚀 synthesizeToWavFile (MINIMAL CHANGE - still queues with utteranceId)
    private fun synthesizeToWavFile(text: String, position: Int) {
        val safeText = text.take(3900)
        val context = requireContext()

        if (safeText.length < text.length) {
            Toast.makeText(context, "Text truncated for TTS (too long)", Toast.LENGTH_SHORT).show()
        }

        try {
            val timestamp = System.currentTimeMillis()
            val utteranceId = "TTS_SAVE_${timestamp}_${position}"
            val tempFile = File(context.cacheDir, "temp_tts_${timestamp}.wav")
            // val fileName = "TTS_${timestamp}_msg${position}.wav"  // For Toast tracking

            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }

            val result = textToSpeech.synthesizeToFile(
                safeText,
                params,
                tempFile,
                utteranceId
            )

            when (result) {
                TextToSpeech.SUCCESS -> {
                    Toast.makeText(context, "Audio generating...", Toast.LENGTH_SHORT).show()
                    //  Log.d("TTS", "✅ Queued TTS: $utteranceId → ${tempFile.absolutePath}")
                }
                else -> {
                    Toast.makeText(context, "❌ TTS wav failed (code: $result)", Toast.LENGTH_SHORT).show()
                    //   Log.e("TTS", "synthesizeToFile failed: $result")
                }
            }

        } catch (e: Exception) {
            //  Log.e("TTS", "Queue error", e)
            Toast.makeText(context, "Error queuing TTS: ${e.message}", Toast.LENGTH_SHORT).show()
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
            isSpeaking = true
            currentSpeakingPosition = position
            chatAdapter.updateTtsState(isSpeaking, currentSpeakingPosition)
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
        overlayView?.visibility = View.VISIBLE
        headerContainer.visibility = View.VISIBLE
        (chatFrameView as ViewGroup).bringChildToFront(headerContainer)
        dimOverlay?.visibility = View.VISIBLE

    }
    private fun hideMenu() {
        dimOverlay?.visibility = View.GONE
        headerContainer.visibility = View.GONE
        overlayView?.visibility = View.GONE
    }
    private fun scrollToPreviousScreen() {
        chatRecyclerView.post {
            val height = chatRecyclerView.height
            chatRecyclerView.smoothScrollBy(0, -height)
            updateScrollButtonsVisibility()
            //Toast.makeText(requireContext(), "Scrolled up one screen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scrollToNextScreen() {
        chatRecyclerView.post {
            val height = chatRecyclerView.height
            chatRecyclerView.smoothScrollBy(0, height)
            updateScrollButtonsVisibility()
        }
    }

    private fun updateScrollButtonsVisibility() {
        if (!sharedPreferencesHelper.getScrollersPreference()) return
        chatRecyclerView.post {
            val canScrollUp = chatRecyclerView.canScrollVertically(-1)
            val canScrollDown = chatRecyclerView.canScrollVertically(1)
            scrollToTopButton.visibility = if (canScrollUp) View.VISIBLE else View.INVISIBLE
            scrollToBottomButton.visibility = if (canScrollDown) View.VISIBLE else View.INVISIBLE
        }
    }
    private fun showSaveChatDialogWithResultApi() {
        val dialog = SaveChatDialogFragment()
        childFragmentManager.setFragmentResultListener(SaveChatDialogFragment.REQUEST_KEY, viewLifecycleOwner) { key, bundle ->
            if (key == SaveChatDialogFragment.REQUEST_KEY) {
                val title = bundle.getString(SaveChatDialogFragment.BUNDLE_KEY_TITLE)
                if (!title.isNullOrBlank()) {
                    // UPDATED: Extract saveAsNew and pass to ViewModel
                    val saveAsNew = bundle.getBoolean("save_as_new", false)
                    viewModel.saveCurrentChat(title, saveAsNew)

                    // Optional: Feedback
                   // val sessionId = bundle.getLong("session_id", -1L)  // Still available for toasts if needed
                    if (saveAsNew) {
                        // e.g., Toast.makeText(context, "Chat saved as new!", Toast.LENGTH_SHORT).show()
                    } else {
                        // e.g., Toast.makeText(context, "Chat updated!", Toast.LENGTH_SHORT).show()
                    }
                    // buttonsContainer.visibility = View.GONE
                    // modelNameTextView.isVisible = false
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
    private fun setupTextFilePicker() {
        attachmentButton.setOnClickListener {
            // Launch multi-picker with primary MIME (broadens to text-like; client-side filters the rest)
            val mimeType = "*/*"  // Or "text/plain" for stricter start; fallback handles .kt etc.
            textFilePicker.launch(mimeType)
        }

        attachmentButton.setOnLongClickListener {
            showAttachedFiles()
            true
        }
    }

    private fun setupPlusButtonListener() {
        plusButton.setOnClickListener {
            chatEditText.hideKeyboard()
            val model = viewModel.activeChatModel.value
            if (model == null || !viewModel.isVisionModel(model)) {
                Toast.makeText(requireContext(), "Image/PDF selection not supported for the current model.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // NEW: Check if vision model supports PDF (all do, but future-proof)
            val supportsPdf = true // Or add model check if needed

            val items = mutableListOf("Take a Photo", "Choose from Gallery")
            if (supportsPdf) items.add("Choose PDF") // NEW: Add PDF option

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Load Image or PDF")
                .setItems(items.toTypedArray()) { _, which ->
                    when (which) {
                        0 -> { // Take Photo
                            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                launchCamera()
                            }
                        }
                        1 -> { // Gallery
                            // Your existing gallery code...
                            val allowedMimeTypes: Array<String> = when {
                                model.lowercase().contains("grok") -> arrayOf("image/jpeg", "image/png")
                                else -> arrayOf("image/jpeg", "image/png", "image/webp")
                            }
                            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "image/*"
                                putExtra(Intent.EXTRA_MIME_TYPES, allowedMimeTypes)
                            }
                            imagePicker.launch(intent)
                        }
                        2 -> { // Choose PDF
                            pdfPicker.launch(arrayOf("application/pdf"))  // NEW: Launches document picker with PDF filter
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
          //  Log.e("ChatFragment", "Failed to start foreground service", e)
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
            convoButton.isSelected = sharedPreferencesHelper.getConversationModeEnabled()
        }
    }

    override fun onResume() {
        super.onResume()
        updateSystemMessageButtonState()
        chatEditText.requestFocus()
        viewModel.checkAdvancedReasoningStatus()
        viewModel._isNotiEnabled.value = sharedPreferencesHelper.getNotiPreference()
        val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(2)

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
        } else if (menuClosedByTouch) {
            menuClosedByTouch = false  // Reset immediately
            return true  // Consume to prevent app hide (menu already closed by touch)
        }
        return false  // Allow normal back (e.g., exit app)
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
    private fun processPdfUri(pdfUri: Uri) {
        lifecycleScope.launch {
            var parcelFd: ParcelFileDescriptor? = null
            var tempPdfFile: File? = null
            try {
                // Try direct ParcelFileDescriptor (OpenDocument makes this reliable)
                parcelFd = withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openFileDescriptor(pdfUri, "r")
                } ?: run {
                    // Fallback copy (rare now)
                   // Log.d("ChatFragment", "Direct access failed; copying PDF to cache...")
                    val inputStream = requireContext().contentResolver.openInputStream(pdfUri)
                        ?: run {
                            Toast.makeText(requireContext(), "No read access to PDF.", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                    val cacheDir = requireContext().cacheDir
                    tempPdfFile = File(cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
                    withContext(Dispatchers.IO) {
                        inputStream.use { input ->
                            FileOutputStream(tempPdfFile!!).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    inputStream.close()

                    ParcelFileDescriptor.open(tempPdfFile!!, ParcelFileDescriptor.MODE_READ_ONLY)
                }

                if (parcelFd == null) {
                    Toast.makeText(requireContext(), "Failed to access PDF.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Create renderer once, pass to branches (branches will close it)
                val pdfRenderer = PdfRenderer(parcelFd)  // Local var for safety
                val pageCount = pdfRenderer.pageCount

                when {
                    pageCount == 0 -> {
                        Toast.makeText(requireContext(), "PDF has no pages.", Toast.LENGTH_SHORT).show()
                        pdfRenderer.close()  // Close if no pages
                        return@launch
                    }
                    pageCount == 1 -> {
                        // Single page: Render and close immediately
                        val bitmap = renderPdfPageToBitmap(pdfRenderer, 0)
                        processPdfBitmap(bitmap, "Page 1 of 1")
                        pdfRenderer.close()  // Close here
                    }
                    else -> {
                        // Multi-page: Pass renderer to dialog (dialog closes after selection)
                        showPageSelectionDialog(pdfRenderer, pageCount)  // No pdfUri needed now
                    }
                }
            } catch (e: Exception) {
              //  Log.e("ChatFragment", "PDF processing error", e)
                val errorMsg = "Failed to process PDF: ${e.message}"
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
            } finally {
                // Only close parcelFd and temp file (renderer closed in branches)
                try {
                    parcelFd?.close()
                    tempPdfFile?.delete()
                } catch (e: Exception) {
                 //   Log.e("ChatFragment", "Error closing PDF resources", e)
                }
            }
        }
    }




    private suspend fun renderPdfPageToBitmap(renderer: PdfRenderer, pageIndex: Int): Bitmap {
        return withContext(Dispatchers.IO) {
            val page = renderer.openPage(pageIndex)
            val width = (page.width * 1.5f).toInt() // Scale for quality (adjust if too big)
            val height = (page.height * 1.5f).toInt()
            val bitmap = createBitmap(width, height)
            val bounds = Rect(0, 0, width, height)
            page.render(bitmap, bounds, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()  // Close page immediately
            bitmap
        }
    }


    private suspend fun processPdfBitmap(bitmap: Bitmap, description: String) {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val bytes = byteArrayOutputStream.toByteArray()

        if (bytes.size > 12_000_000) {
            Toast.makeText(requireContext(), "PDF page too large (max 12MB). Try a different page.", Toast.LENGTH_SHORT).show()
            bitmap.recycle()
            return
        }

        selectedImageBytes = bytes
        selectedImageMime = "image/png"

        // Save PNG to temp file for chat preview
        val cacheDir = requireContext().cacheDir
        val tempPngFile = File(cacheDir, "pdf_page_${System.currentTimeMillis()}.png")
        withContext(Dispatchers.IO) {  // Off UI: Write bytes to file
            tempPngFile.outputStream().use { out ->
                out.write(bytes)
            }
        }
        currentTempImageFile = tempPngFile  // Track for cleanup

        val pngUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            tempPngFile
        )

        // Set for ViewModel (enables bubble preview)
        viewModel.setPendingUserImageUri(pngUri.toString())

        val previewBmp = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        previewImageView.setImageBitmap(previewBmp)
        attachmentPreviewContainer.visibility = View.VISIBLE

        Toast.makeText(requireContext(), "$description converted to image", Toast.LENGTH_SHORT).show()

        // Recycle originals
        bitmap.recycle()
        // After copy
    }




    private fun showPageSelectionDialog(pdfRenderer: PdfRenderer, pageCount: Int) {
        val pageTitles = (1..pageCount).map { "Page $it" }.toTypedArray()
        var selectedPage = 0

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select PDF Page")
            .setSingleChoiceItems(pageTitles, 0) { _, which -> selectedPage = which }
            .setPositiveButton("Convert") { _, _ ->
                // Launch coroutine: Render, process, then close
                lifecycleScope.launch {
                    try {
                        val bitmap = renderPdfPageToBitmap(pdfRenderer, selectedPage)
                        processPdfBitmap(bitmap, "Page ${selectedPage + 1} of $pageCount")
                    } finally {
                        // Close renderer here (after use)—safe, no double-close
                        pdfRenderer.close()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .setOnCancelListener {
                // Close if canceled (no render happened)
                pdfRenderer.close()
            }
            .show()
    }
    private fun processTextFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                // Get file info (your existing query for filename)
                val fileName = try {
                    val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) it.getString(nameIndex) else "unknown.txt"
                        } else "unknown.txt"
                    } ?: "unknown.txt"
                } catch (e: Exception) {
                    "unknown.txt"
                }

                // Get MIME type and extension for validation (before reading content)
                val mimeType = requireContext().contentResolver.getType(uri)
                val extension = fileName.substringAfterLast('.', "").lowercase()

                // TEMP DEBUG LOG: Remove after testing
                // android.util.Log.d("ProcessTextFile", "File: $fileName, MIME: '$mimeType', Extension: '$extension'")

                // Allowed MIME types (your list from earlier)
                val allowedTypes = setOf(
                    "text/plain", "text/html", "text/css", "text/javascript", "application/javascript",
                    "application/json", "application/xml", "text/yaml", "application/toml",
                    "text/csv", "application/sql", "text/markdown", "image/svg+xml"
                )

                // FIXED: Fallback if MIME is known-good OR (unknown/non-text + code extension)
                val isAllowed = if (mimeType != null && allowedTypes.contains(mimeType)) {
                    true  // Known text MIME: Accept
                } else {
                    // MIME is null, unknown (e.g., octet-stream), or non-text: Fallback to extension
                    val codeExtensions = setOf(
                        "kt", "java", "py", "js", "ts", "cpp", "c", "h", "cs", "php", "rb", "go", "rs", "swift",
                        "html", "css", "json", "xml", "yaml", "yml", "md", "txt", "sh", "sql", "csv", "log"
                    )
                    codeExtensions.contains(extension)
                }

                if (!isAllowed) {
                    Toast.makeText(requireContext(), "Unsupported file: $fileName ($mimeType). Please select text/code files.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Check current total size first (your existing logic)
                val currentTotalSize = pendingFiles.sumOf { it.size }

                // Read content and check individual file size (your buffered reading)
                val content = withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        val contentBuilder = StringBuilder()
                        val buffer = CharArray(8192)
                        var charsRead: Int
                        while (reader.read(buffer).also { charsRead = it } > 0) {
                            contentBuilder.append(buffer, 0, charsRead)
                        }
                        contentBuilder.toString()
                    }
                } ?: throw Exception("Could not read file")

                val fileSize = content.toByteArray().size.toLong()

                // Size validation (your existing checks)
                if (fileSize > MAX_SINGLE_FILE_SIZE) {
                    Toast.makeText(requireContext(), "File too large: $fileName (max ${MAX_SINGLE_FILE_SIZE / 1024 / 1024}MB per file)", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (currentTotalSize + fileSize > MAX_FILE_SIZE) {
                    Toast.makeText(requireContext(), "Total attachments exceed limit: $fileName would make ${(currentTotalSize + fileSize) / 1024 / 1024}MB (max ${MAX_FILE_SIZE / 1024 / 1024}MB total)", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Add to pending files (your existing AttachedFile)
                pendingFiles.add(AttachedFile(fileName, content, fileSize))

                // Update UI (your existing)
                updateAttachmentButton()
                Toast.makeText(requireContext(), "File attached: $fileName", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }



    private fun updateAttachmentButton() {
        attachmentButton.isSelected = pendingFiles.isNotEmpty()
    }

    private fun showAttachedFiles() {
        if (pendingFiles.isEmpty()) return

        val filesList = pendingFiles.joinToString("\n") { file ->
            "${file.fileName} (${formatFileSize(file.size)})"
        }

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Attached Files (${pendingFiles.size})")
            .setMessage(filesList)
            .setPositiveButton("Remove All") { _, _ ->
                pendingFiles.clear()
                updateAttachmentButton()
            }
            .setNegativeButton("Close", null)

        val dialog = builder.show()

        // Apply dim amount of 0.8f
        dialog.window?.setDimAmount(0.8f)
    }

    private fun showWebSearchEngineDialog() {
        val engines = listOf(
            "default" to "Default (Native if available, fallback Exa)",
            "native" to "Native (Provider's built-in search)",
            "exa" to "Exa (Always use Exa search)"
        )

        val currentEngine = sharedPreferencesHelper.getWebSearchEngine()
        var selectedEngine = currentEngine  // Track selection locally

        MaterialAlertDialogBuilder(requireContext(),
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered
        )
            .setTitle("Web Search Engine")
            .setSingleChoiceItems(
                engines.map { it.second }.toTypedArray(),
                engines.indexOfFirst { it.first == currentEngine }
            ) { _, which ->
                selectedEngine = engines[which].first  // Update local selection only
            }
            .setPositiveButton("Save") { _, _ ->
                // Save only when Save is pressed
                sharedPreferencesHelper.saveWebSearchEngine(selectedEngine)
                Toast.makeText(
                    requireContext(),
                    "Web search engine: ${engines.find { it.first == selectedEngine }?.second}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> String.format("%.1fKB", bytes / 1024f)
            else -> String.format("%.1fMB", bytes / 1024f / 1024f)
        }
    }
    fun TextView.animateColor(from: Int, to: Int, dur: Long): ValueAnimator? =
        ValueAnimator.ofArgb(from, to).apply {
            duration = dur
            addUpdateListener { setTextColor(it.animatedValue as Int) }
            start()
        }
    private fun captureItemToPng(position: Int) {
        // Get the ViewHolder for the position (if visible)
        val viewHolder = chatRecyclerView.findViewHolderForAdapterPosition(position) as? ChatAdapter.AssistantViewHolder
        if (viewHolder != null) {
            val bitmap = captureViewToBitmap(viewHolder.messageContainer)
            if (bitmap != null) {
                viewModel.saveBitmapToDownloads(bitmap)
            } else {
                Toast.makeText(requireContext(), "Failed to capture view", Toast.LENGTH_SHORT).show()
            }
        } else {
            // If not visible, we can still try to capture by creating a temporary view.
            // For simplicity, we’ll just toast that the item isn’t visible.
            Toast.makeText(requireContext(), "Item not visible; cannot capture", Toast.LENGTH_SHORT).show()
        }
    }

    fun captureViewToBitmap(view: View): Bitmap? {
        // If the view is already laid out, use its current size.
        if (view.width > 0 && view.height > 0) {
            val bitmap = createBitmap(view.width, view.height)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            return bitmap
        }

        // If not laid out, measure and layout manually.
        val widthSpec = View.MeasureSpec.makeMeasureSpec(view.layoutParams.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        val measuredHeight = view.measuredHeight
        val heightSpecExact = View.MeasureSpec.makeMeasureSpec(measuredHeight, View.MeasureSpec.EXACTLY)
        view.measure(widthSpec, heightSpecExact)

        val bitmap = createBitmap(view.measuredWidth, view.measuredHeight)
        val canvas = Canvas(bitmap)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        view.draw(canvas)
        return bitmap
    }
    private fun printChatHtml(htmlContent: String) {
        val printManager = requireContext().getSystemService(Context.PRINT_SERVICE) as PrintManager
        val currentModel = viewModel._activeChatModel.value ?: "Unknown"
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
        val dateTime = sdf.format(Date())
        val adapterTitle = "${currentModel.replace("/", "-")}_$dateTime"  // ✅ "x-ai-grok-4.1-fast_2024-10-05_14-30.pdf"
        val jobName = "Chat History"

        val webView = WebView(requireContext()).apply {
            settings.apply {
                javaScriptEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                defaultTextEncodingName = "utf-8"
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.createPrintDocumentAdapter(adapterTitle)?.let { adapter ->
                        printManager.print(jobName, adapter, null)
                    }
                }
            }

            // ✅ FIXED: HR lines HIDDEN in print (no sep after user OR assistant). Spacers ONLY via margins: tiny after user (flush to assistant), 2em ONLY after assistant. Clean flow!
            val fullHtml = """
        <!DOCTYPE html>
        <html><head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Chat History</title>
            <style>
                * { box-sizing: border-box; }
                body { 
                    margin: 40px 20px;  
                    padding: 0;         
                    max-width: 100%;    
                    font-family: -apple-system,BlinkMacSystemFont,"Segoe UI",Helvetica,Arial,sans-serif,"Apple Color Emoji","Segoe UI Emoji";
                    font-size: 16px; line-height: 1.5; color: #24292f; background: white;
                }
                .markdown-body { font-size: 16px; line-height: 1.5; }
                h1 { 
                    color: #24292f !important; font-size: 2em !important; font-weight: 600 !important; 
                    text-decoration: underline !important;
                    border-bottom: none !important;
                    padding-bottom: .3em !important; margin: 0 0 1em 0 !important; 
                }
                a { color: #0366d6; text-decoration: none; }
                a:hover, a:focus { text-decoration: underline; }
                @media print { a { text-decoration: underline !important; color: #0366d6 !important; } }
                strong { font-weight: 600; }
                pre, code { font-family: 'SFMono-Regular',Consolas,'Liberation Mono',Menlo,monospace; font-size: 14px; }
                code { background: #f6f8fa; border-radius: 6px; padding: .2em .4em; }
                pre { background: #f6f8fa; border-radius: 6px; padding: 16px; overflow: auto; margin: 1em 0; }
                blockquote { border-left: 4px solid #dfe2e5; color: #6a737d; padding-left: 1em; margin: 1em 0; }
                table { border-collapse: collapse; width: 100%; margin: 1em 0; }
                th, td { border: 1px solid #d0d7de; padding: .75em; text-align: left; }
                th { background: #f6f8fa; font-weight: 600; }
                hr { border: none; border-top: 1px solid #eaecef; height: 0; margin: 1.5em 0; }
                ul, ol { padding-left: 2em; margin: 1em 0; }
                img { max-width: 100%; height: auto; }
                del { color: #bd2c00; }
                input[type="checkbox"] { margin: 0 .25em 0 0; vertical-align: middle; }
                
                /* Screen tweaks */
                h3[style*="28a745"] + div[style*="background"] {
                    background: #f8f9fa; border-left-color: #28a745;
                }
                
                /* ✅ PRINT: NO HR LINES. Margins ONLY for spacers */
                @media print {
                    body { 
                        margin: 0.5in 0.25in !important;  
                        padding: 0 !important;
                        max-width: none !important;
                        font-size: 12pt !important; line-height: 1.5 !important;
                    }
                    h1 { 
                        page-break-after: avoid; 
                        text-decoration: underline !important; 
                        border-bottom: none !important; 
                    }
                    
                    /* ✅ NO SEPARATOR LINES: Hide all HR */
                    hr { 
                        display: none !important;  /* ✅ GONE – no lines after user OR assistant */
                    }
                    
                    /* ✅ SPACERS VIA MARGINS ONLY: Tiny after USER (flush to assistant). 2em ONLY after ASSISTANT */
                    div[style*="margin-bottom: 2em"]:has(h3[style*="0366d6"]) {
                        margin-bottom: 0.25em !important;  /* ✅ User → assistant: tight */
                    }
                    div[style*="margin-bottom: 2em"]:has(h3[style*="28a745"]) {
                        margin-bottom: 2em !important;  /* ✅ Assistant → next user: spacer ONLY here */
                    }
                    
                    /* ✅ ASSISTANT: Plain text (no bg/border) */
                    h3[style*="28a745"] + div[style*="background: #f6f8fa"],
                    h3[style*="28a745"] + div {
                        background: none !important;
                        background-color: transparent !important;
                        border: none !important;
                        border-left: none !important;
                        border-left-color: transparent !important;
                        padding: 0.25em 0.5em !important;
                        border-radius: 0 !important;
                        margin: 0 !important;
                    }
                    
                    /* USER: Keep bg/border */
                    h3[style*="0366d6"] + div[style*="background: #f6f8fa"] {
    padding: 0.05em 0.5em !important;  /* ✅ Tight user bg in print */
}

                    
                    pre { white-space: pre-wrap; }
                    @page { margin: 0.5in; }
                }
            </style>
        </head><body>
            <div class="markdown-body">$htmlContent</div>
        </body></html>
        """.trimIndent()

            loadDataWithBaseURL(null, fullHtml, "text/html", "UTF-8", null)
        }
    }
}
