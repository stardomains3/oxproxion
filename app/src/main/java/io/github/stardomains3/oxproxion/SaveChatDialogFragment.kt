package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

// REMOVED: interface SaveChatDialogListener { ... }
// REMOVED: private var listener: SaveChatDialogListener? = null

class SaveChatDialogFragment : DialogFragment() {
    private lateinit var chatViewModel: ChatViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.RoundedCornersDialog)
        chatViewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]

        // Optionally apply a dialog style for rounded corners, full width, etc.
        // For example: style = R.style.FullScreenDialog or a custom theme
        // For a standard material dialog look, you might not need a custom style here.
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate your custom layout
        return inflater.inflate(R.layout.dialog_save_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.apply {
            // Set dialog width to match parent, height to wrap content
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

            // Set gravity to top-center
            setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)

            // Optional: Add a small top margin/offset if you don't want it exactly at the very top
            // val params = attributes
            // params.y = resources.getDimensionPixelSize(R.dimen.dialog_top_margin) // Define this dimen in dimens.xml (e.g., <dimen name="dialog_top_margin">64dp</dimen>)
            // attributes = params
        }
        val editTextTitle = view.findViewById<TextInputEditText>(R.id.edit_text_title)
        val buttonSave = view.findViewById<MaterialButton>(R.id.button_save)
        val buttonCancel = view.findViewById<MaterialButton>(R.id.button_cancel)
        val buttonLlmSuggestName = view.findViewById<MaterialButton>(R.id.button_suggest_title)


        buttonSave.setOnClickListener {
            val title = editTextTitle.text.toString()
            if (title.isNotBlank()) {
                // Use Fragment Result API to send data back
                setFragmentResult(REQUEST_KEY, bundleOf(BUNDLE_KEY_TITLE to title))
                dismiss() // Close the dialog
            } else {
                editTextTitle.error = "Title cannot be empty" // Show an error
            }
        }
        buttonLlmSuggestName.setOnClickListener {
            // Provide immediate feedback and disable button
            buttonLlmSuggestName.isEnabled = false
            editTextTitle.setText("Generating title...") // Indicate loading
            editTextTitle.error = null // Clear any previous errors

            // Launch a coroutine in the fragment's lifecycle scope
            lifecycleScope.launch {
                val suggestedTitle = chatViewModel.getSuggestedChatTitle()
                if (suggestedTitle != null) {
                    editTextTitle.setText(suggestedTitle)
                } else {
                    editTextTitle.setText("") // Clear or keep "Generating title..."
                    editTextTitle.error = "Failed to suggest title. Please enter manually."
                }
                buttonLlmSuggestName.isEnabled = true // Re-enable button
            }
        }

        buttonCancel.setOnClickListener {
            dismiss() // Close the dialog
        }

        // Request focus and show keyboard automatically
        editTextTitle.requestFocus()
        // Ensure the window is not null before trying to set soft input mode
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    // REMOVED: fun setSaveChatDialogListener(listener: SaveChatDialogListener) { ... }

    companion object {
        const val TAG = "SaveChatDialogFragment"
        const val REQUEST_KEY = "save_chat_request"
        const val BUNDLE_KEY_TITLE = "chat_title"
    }
}