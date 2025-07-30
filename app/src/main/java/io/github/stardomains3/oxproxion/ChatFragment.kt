package io.github.stardomains3.oxproxion

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.html.HtmlPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

class ChatFragment : Fragment(R.layout.fragment_chat) {

    private var selectedImageBytes: ByteArray? = null
    private var selectedImageMime: String? = null
    private lateinit var plusButton: MaterialButton
    private var serviceStarted = false
    private var originalSendIcon: Drawable? = null


    private val viewModel: ChatViewModel by activityViewModels()

    private lateinit var modelNameTextView: TextView
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var chatEditText: EditText
    private lateinit var sendChatButton: Button
    private lateinit var resetChatButton: Button
    private lateinit var saveChatButton: Button
    private lateinit var openSavedChatsButton: Button
    private lateinit var copyChatButton: Button
    private lateinit var menuButton: Button
    private lateinit var saveapiButton: Button
    private lateinit var pdfChatButton: MaterialButton
    private lateinit var systemMessageButton: MaterialButton
    private lateinit var streamButton: MaterialButton
    private lateinit var buttonsContainer: LinearLayout
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var markwon: Markwon
    private lateinit var pdfGenerator: PdfGenerator
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper

    private lateinit var attachmentPreviewContainer: View
    private lateinit var previewImageView: ImageView
    private lateinit var removeAttachmentButton: ImageButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferencesHelper = SharedPreferencesHelper(requireContext())

        // --- Initialize Views from fragment_chat.xml ---

        pdfChatButton = view.findViewById(R.id.pdfChatButton)
        systemMessageButton = view.findViewById(R.id.systemMessageButton)
        streamButton = view.findViewById(R.id.streamButton)

        chatRecyclerView = view.findViewById(R.id.chatRecyclerView)
        chatEditText = view.findViewById(R.id.chatEditText)
        sendChatButton = view.findViewById(R.id.sendChatButton)
        if (sendChatButton is MaterialButton) {
            originalSendIcon = (sendChatButton as MaterialButton).icon
        }
        resetChatButton = view.findViewById(R.id.resetChatButton)
        saveChatButton = view.findViewById(R.id.saveChatButton)
        openSavedChatsButton = view.findViewById(R.id.openSavedChatsButton)
        copyChatButton = view.findViewById(R.id.copyChatButton)
        menuButton = view.findViewById(R.id.menuButton)
        saveapiButton = view.findViewById(R.id.saveapiButton)
        buttonsContainer = view.findViewById(R.id.buttonsContainer)
        modelNameTextView = view.findViewById(R.id.modelNameTextView)
        attachmentPreviewContainer = view.findViewById(R.id.attachmentPreviewContainer)
        previewImageView = view.findViewById(R.id.previewImageView)
        removeAttachmentButton = view.findViewById(R.id.removeAttachmentButton)
        markwon = Markwon.builder(requireContext())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .codeTextColor(Color.LTGRAY)
                        .codeBackgroundColor(Color.DKGRAY)
                        .codeBlockBackgroundColor(Color.DKGRAY)
                        .blockQuoteColor(Color.BLACK)
                        .isLinkUnderlined(true)
                }
            })
            .build()
        setupRecyclerView()
        setupClickListeners()
        pdfGenerator = PdfGenerator(requireContext())
        plusButton = view.findViewById(R.id.plusButton)
        setupImagePicker()

        viewModel.activeChatModel.observe(viewLifecycleOwner) { model ->
            if (model != null) {
                modelNameTextView.text = formatModelName(model)
                plusButton.visibility = if (viewModel.isVisionModel(model)) View.VISIBLE else View.GONE

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
        modelNameTextView.isVisible = true
        /*  Handler(Looper.getMainLooper()).postDelayed(1600) {
              if(!buttonsContainer.isVisible){
                  modelNameTextView.isVisible = false}
          }*/
        // --- Observe LiveData ---
        viewModel.chatMessages.observe(viewLifecycleOwner) { messages ->
            chatAdapter.setMessages(messages)
            val hasMessages = messages.isNotEmpty()
            saveChatButton.isVisible = hasMessages
            resetChatButton.isVisible = hasMessages
            pdfChatButton.isVisible = hasMessages
            copyChatButton.isVisible = hasMessages
        }

        viewModel.isAwaitingResponse.observe(viewLifecycleOwner) { isAwaiting ->
            sendChatButton.isEnabled = true
            if (sendChatButton is MaterialButton) {
                if (isAwaiting) {
                    (sendChatButton as MaterialButton).setIconResource(R.drawable.ic_stop)
                } else {
                    (sendChatButton as MaterialButton).icon = originalSendIcon
                }
            }
        }

        viewModel.modelPreferenceToSave.observe(viewLifecycleOwner) { model ->
            model?.let {
                sharedPreferencesHelper.savePreferenceModelnewchat(it)
                viewModel.onModelPreferenceSaved()
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

        viewModel.scrollToBottomEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                if (chatAdapter.itemCount > 0) {
                    val layoutManager = chatRecyclerView.layoutManager as LinearLayoutManager
                    layoutManager.scrollToPositionWithOffset(chatAdapter.itemCount - 1, 0)
                }
            }
        }


    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(markwon)
        chatRecyclerView.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
        }
    }
    private fun hideKeyboard() {
        val inputMethodManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocusedView = activity?.currentFocus
        if (currentFocusedView != null) {
            inputMethodManager.hideSoftInputFromWindow(currentFocusedView.windowToken, 0)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        removeAttachmentButton.setOnClickListener {
            selectedImageBytes = null
            selectedImageMime = null
            attachmentPreviewContainer.visibility = View.GONE
            Toast.makeText(requireContext(), "Attachment removed", Toast.LENGTH_SHORT).show()
        }
        sendChatButton.setOnClickListener {
            if (viewModel.isAwaitingResponse.value == true) {
                viewModel.cancelCurrentRequest()
            } else {
                // --- API Key Check ---
                if (viewModel.activeChatApiKey.isBlank()) {
                    Toast.makeText(requireContext(), "API Key is not set.", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener // Stop the process
                }

                val prompt = chatEditText.text.toString().trim()
                if (prompt.isNotBlank() || selectedImageBytes != null) {
                    if (!serviceStarted) {
                        startForegroundService()
                        serviceStarted = true
                    }
                    chatEditText.text.clear()
                    hideKeyboard()
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
                    buttonsContainer.visibility = View.GONE
                    modelNameTextView.isVisible = false
                    selectedImageBytes = null
                    selectedImageMime = null
                    attachmentPreviewContainer.visibility = View.GONE
                    if (chatAdapter.itemCount > 0) {
                        chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                    }
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
                        /* if(!buttonsContainer.isVisible)
                         {
                             modelNameTextView.isVisible = true
                             Handler(Looper.getMainLooper()).postDelayed(600) {
                                 modelNameTextView.isVisible = false
                             }
                         }
                         else if(buttonsContainer.isVisible){
                             Handler(Looper.getMainLooper()).postDelayed(600) {
                                 modelNameTextView.isVisible = false
                                 buttonsContainer.isVisible = false

                             }
                         }*/
                    }
                }
            }
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, picker)
                .addToBackStack(null)
                .commit()
        }

        systemMessageButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SystemMessageLibraryFragment())
                .addToBackStack(null)
                .commit()
        }

        resetChatButton.setOnClickListener {
            if (viewModel.chatMessages.value.isNullOrEmpty()) {
                //  buttonsContainer.visibility = View.GONE
                // modelNameTextView.isVisible = false
                return@setOnClickListener
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Start New Chat?")
                .setMessage("Are you sure you want to clear the current conversation? This action cannot be undone.")
                .setNegativeButton("Cancel") { dialog, which ->
                    dialog.dismiss()
                }
                .setPositiveButton("Reset") { dialog, which ->
                    viewModel.startNewChat()
                    if (serviceStarted) {
                        stopForegroundService()
                        serviceStarted = false
                    }
                    //  buttonsContainer.visibility = View.GONE
                    // modelNameTextView.isVisible = false
                }
                .show()
        }

        saveChatButton.setOnClickListener {
            if (viewModel.chatMessages.value.isNullOrEmpty()) {
                // buttonsContainer.visibility = View.GONE
                //modelNameTextView.isVisible = false
                return@setOnClickListener
            }
            else {
                // buttonsContainer.visibility = View.GONE
                // modelNameTextView.isVisible = false
                showSaveChatDialogWithResultApi()
            }
        }

        openSavedChatsButton.setOnClickListener {
            //  buttonsContainer.visibility = View.GONE
            // modelNameTextView.isVisible = false
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SavedChatsFragment())
                .addToBackStack(null)
                .commit()

        }

        pdfChatButton.setOnClickListener {
            // buttonsContainer.visibility = View.GONE
            val messages = viewModel.chatMessages.value ?: emptyList()

            if (messages.isEmpty()) {
                //  buttonsContainer.visibility = View.GONE
                // modelNameTextView.isVisible = false
                Toast.makeText(requireContext(), "No chat history to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pdfChatButton.setIconResource(R.drawable.ic_check)
            Handler(Looper.getMainLooper()).postDelayed({
                if (buttonsContainer.isVisible) {
                    buttonsContainer.visibility = View.GONE
                    modelNameTextView.isVisible = false
                }
                pdfChatButton.setIconResource(R.drawable.ic_pdfnew)
            }, 500)

            lifecycleScope.launch {
                // Toast.makeText(requireContext(), "Generating PDF...", Toast.LENGTH_SHORT).show()
                val modelIdentifier = viewModel.activeChatModel.value ?: "Unknown Model"
                val modelName = viewModel.getModelDisplayName(modelIdentifier)
                val filePath = withContext(Dispatchers.IO) {
                    try {
                        if (viewModel.hasImagesInChat()) {
                            pdfGenerator.generateStyledChatPdfWithImages(requireContext(), messages, modelName)
                        } else {
                            val markdownText = viewModel.getFormattedChatHistory()
                            pdfGenerator.generateStyledChatPdf(requireContext(), markdownText, modelName)
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

        copyChatButton.setOnClickListener {
            val chatText = viewModel.getFormattedChatHistory()
            if (chatText.isNotBlank()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Chat History", chatText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Chat Copied!", Toast.LENGTH_SHORT).show()
                //   buttonsContainer.visibility = View.GONE
                // modelNameTextView.isVisible = false
            }
            else{
                //    buttonsContainer.visibility = View.GONE
                //  modelNameTextView.isVisible = false
                Toast.makeText(requireContext(), "Nothing to Copy", Toast.LENGTH_SHORT).show()
            }
        }

        menuButton.setOnClickListener {
            val isMenuVisible = !buttonsContainer.isVisible
            buttonsContainer.visibility = if (isMenuVisible) View.VISIBLE else View.GONE
            modelNameTextView.isVisible = isMenuVisible
        }

        saveapiButton.setOnClickListener {
            val dialog = SaveApiDialogFragment()
            dialog.show(childFragmentManager, "SaveApiDialogFragment")
        }

        saveapiButton.setOnLongClickListener {
            if (viewModel.activeChatApiKey.isBlank()) {
                Toast.makeText(requireContext(), "API Key is not set.", Toast.LENGTH_SHORT).show()
            } else {
                //  Toast.makeText(requireContext(), "Checking credits...", Toast.LENGTH_SHORT).show()
                viewModel.checkRemainingCredits()
            }
            true // Consume the long click
        }
        modelNameTextView.setOnLongClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://openrouter.ai/models"))
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

        menuButton.setOnLongClickListener {
            // This can be used for other future menu-related long-press actions
            true // Consume the long click
        }
    }

    private fun showSaveChatDialogWithResultApi() {
        val dialog = SaveChatDialogFragment()
        childFragmentManager.setFragmentResultListener(SaveChatDialogFragment.REQUEST_KEY, viewLifecycleOwner) { key, bundle ->
            if (key == SaveChatDialogFragment.REQUEST_KEY) {
                val title = bundle.getString(SaveChatDialogFragment.BUNDLE_KEY_TITLE)
                if (!title.isNullOrBlank()) {
                    viewModel.saveCurrentChat(title)
                    //  buttonsContainer.visibility = View.GONE
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

    private fun setupImagePicker() {
        val imagePicker =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uri: Uri? = result.data?.data
                    uri?.let { u ->
                        requireContext().contentResolver.openInputStream(u)?.use { stream ->
                            val bytes = stream.readBytes()
                            if (bytes.size > 2_300_000) {
                                Toast.makeText(
                                    requireContext(),
                                    "Image too large (max 2.3MB)",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@use
                            }
                            val mime = requireContext().contentResolver.getType(u)
                            val ext = when (mime) {
                                "image/jpeg" -> "jpeg"
                                "image/png" -> "png"
                                "image/webp" -> "webp"
                                else -> {
                                    Toast.makeText(
                                        requireContext(),
                                        "Unsupported image format",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@use
                                }
                            }
                            selectedImageBytes = bytes
                            selectedImageMime = mime
                            previewImageView.setImageURI(u)
                            attachmentPreviewContainer.visibility = View.VISIBLE
                        }
                    }
                }
            }

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

            val allowedMimeTypes: Array<String> = when {
                model.lowercase().contains("google") || model.lowercase().contains("chatgpt-4o-latest")|| model.lowercase().contains("claude-sonnet-4")|| model.lowercase().contains("gpt-4.1")|| model.lowercase().contains("gemini") || model.lowercase().contains("maverick")|| model.lowercase().contains("mistral-medium-3") -> {
                    arrayOf("image/jpeg", "image/png", "image/webp")
                }
                model.lowercase().contains("grok") -> {
                    arrayOf("image/jpeg", "image/png")
                }
                else -> {
                    arrayOf("image/*")
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

    private fun startForegroundService() {
        try {
            val serviceIntent = Intent(requireContext(), ForegroundService::class.java)
            requireContext().startService(serviceIntent)
        } catch (e: Exception) {
            Log.e("ChatFragment", "Failed to start foreground service", e)
        }
    }

    private fun stopForegroundService() {
        try {
            ForegroundService.stopService()
        } catch (e: Exception) {
            Log.e("ChatFragment", "Failed to stop foreground service", e)
        }
    }

    override fun onResume() {
        super.onResume()
        // if (isInitialCreate) {
        //     isInitialCreate = false
        //  } else {
      //  modelNameTextView.isVisible = true
       // buttonsContainer.isVisible = true
        /*  Handler(Looper.getMainLooper()).postDelayed(2400) {
             // if (!buttonsContainer.isVisible)
              //{
                  buttonsContainer.isVisible = false
                  modelNameTextView.isVisible = false
              //}
          }*/
        //  }
        if (viewModel.chatMessages.value.isNullOrEmpty()) {
            showKeyboard()
        }
    }

    private fun showKeyboard() {
        chatEditText.requestFocus()
        val inputMethodManager =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showSoftInput(chatEditText, InputMethodManager.SHOW_IMPLICIT)
    }
    fun onBackPressed(): Boolean {
        if (buttonsContainer.isVisible) {
            buttonsContainer.isVisible = false
            modelNameTextView.isVisible = false
            return true
        }
        return false
    }
}
