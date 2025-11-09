package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class SaveChatDialogFragment : DialogFragment() {
    private lateinit var chatViewModel: ChatViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.RoundedCornersDialog)
        chatViewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_save_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        }
        val editTextTitle = view.findViewById<TextInputEditText>(R.id.edit_text_title)
        val buttonSave = view.findViewById<MaterialButton>(R.id.button_save)
        val buttonCancel = view.findViewById<MaterialButton>(R.id.button_cancel)
        val buttonLlmSuggestName = view.findViewById<MaterialButton>(R.id.button_suggest_title)
        val noticeTextView = view.findViewById<TextView>(R.id.notice_text_view)
        // NEW: Checkbox for save as new
        val checkboxSaveAsNew = view.findViewById<CheckBox>(R.id.checkbox_save_as_new)

        if (chatViewModel.hasImagesInChat() || chatViewModel.hasGeneratedImagesInChat()) {
            noticeTextView.visibility = View.VISIBLE
        } else {
            noticeTextView.visibility = View.GONE
        }

        buttonSave.setOnClickListener {
            val title = editTextTitle.text.toString()
            if (title.isNotBlank()) {
                val currentSessionId = chatViewModel.getCurrentSessionId()
                val bundle = bundleOf(BUNDLE_KEY_TITLE to title)
                // NEW: Always include save_as_new (default false)
                bundle.putBoolean("save_as_new", checkboxSaveAsNew.isChecked)
                if (currentSessionId != null && !checkboxSaveAsNew.isChecked) {
                    // Backward compatible: only include for overwrite mode
                    bundle.putLong("session_id", currentSessionId)
                    bundle.putBoolean("is_update", true)
                }
                setFragmentResult(REQUEST_KEY, bundle)
                dismiss()
            } else {
                editTextTitle.error = "Title cannot be empty"
            }
        }
        buttonLlmSuggestName.setOnClickListener {
            buttonLlmSuggestName.isEnabled = false
            editTextTitle.setText("Generating title...")
            editTextTitle.error = null

            lifecycleScope.launch {
                val suggestedTitle = chatViewModel.getSuggestedChatTitle()
                if (suggestedTitle != null) {
                    editTextTitle.setText(suggestedTitle)
                } else {
                    editTextTitle.setText("")
                    editTextTitle.error = "Failed to suggest title. Please enter manually."
                }
                buttonLlmSuggestName.isEnabled = true
            }
        }

        buttonCancel.setOnClickListener {
            dismiss()
        }

        // NEW/UPDATED: Handle prefilling and checkbox visibility
        lifecycleScope.launch {
            val currentSessionId = chatViewModel.getCurrentSessionId()
            if (currentSessionId != null) {
                // Loaded chat: Show checkbox (default false = overwrite), prefill title, update notice
                checkboxSaveAsNew.visibility = View.VISIBLE
                checkboxSaveAsNew.isChecked = false  // Default to overwrite
                val existingTitle = chatViewModel.getCurrentSessionTitle()
                if (!existingTitle.isNullOrBlank()) {
                    editTextTitle.setText(existingTitle)
                }
                // Optional: Update notice to hint at overwrite vs. new
                //  noticeTextView.text = "Modifications will overwrite the existing chat unless 'Save as new chat' is checked."
                //noticeTextView.visibility = View.VISIBLE  // Always show for loaded chats
            } else {
                // New chat: Hide checkbox (always saves as new), no special notice
                checkboxSaveAsNew.visibility = View.GONE
                // noticeTextView.visibility = if (chatViewModel.hasImagesInChat() || chatViewModel.hasGeneratedImagesInChat()) View.VISIBLE else View.GONE
            }

            editTextTitle.requestFocus()
            dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
    }

    companion object {
        const val TAG = "SaveChatDialogFragment"
        const val REQUEST_KEY = "save_chat_request"
        const val BUNDLE_KEY_TITLE = "chat_title"
    }
}
