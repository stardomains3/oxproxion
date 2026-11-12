package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlin.text.set

class AddEditSystemMessageFragment : Fragment() {

    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private var originalTitle: String? = null

    companion object {
        const val ARG_TITLE = "arg_title"
        const val ARG_PROMPT = "arg_prompt"
        const val ARG_IS_DEFAULT = "arg_is_default"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_edit_system_message, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferencesHelper = SharedPreferencesHelper(requireContext())

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val titleEditText = view.findViewById<EditText>(R.id.edit_text_system_message_title)
        val promptEditText = view.findViewById<EditText>(R.id.edit_text_system_message_prompt)

        originalTitle = arguments?.getString(ARG_TITLE)
        val originalPrompt = arguments?.getString(ARG_PROMPT)

        if (originalTitle != null) {
            toolbar.title = "Edit System Message"
            titleEditText.setText(originalTitle)
            promptEditText.setText(originalPrompt)
        } else {
            toolbar.title = "Add System Message"
        }

        val isDefaultMessage = arguments?.getBoolean(ARG_IS_DEFAULT, false) ?: false

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_save_system_message -> {
                    val newTitle = titleEditText.text.toString().trim()
                    val newPrompt = promptEditText.text.toString()  // intentionally NOT trimmed yet

                    if (newTitle.isBlank()) {
                        Snackbar.make(requireView(), "Title is required", Snackbar.LENGTH_LONG)
                            .setAction("OK") { }
                            .show()
                        return@setOnMenuItemClickListener true
                    }

                    // NEW: Empty prompt is now allowed, but confirm so it's not accidental
                    if (newPrompt.isBlank()) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Save with empty prompt?")
                            .setMessage("An empty system prompt means requests will be sent without a system message (default model behavior).")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Save") { _, _ ->
                                saveMessage(newTitle, newPrompt, isDefaultMessage)
                            }
                            .show()
                        return@setOnMenuItemClickListener true
                    }

                    saveMessage(newTitle, newPrompt, isDefaultMessage)
                    true
                }

                else -> false
            }
        }


    }
    private fun saveMessage(newTitle: String, newPrompt: String, isDefaultMessage: Boolean) {
        // === Existing duplicate check, verbatim ===
        val defaultMessage = sharedPreferencesHelper.getDefaultSystemMessage()
        val customMessages = sharedPreferencesHelper.getCustomSystemMessages()
        val allMessages = customMessages + defaultMessage

        val hasDuplicate = allMessages.any { existingMessage ->
            val existingTitleTrimmed = existingMessage.title.trim()
            val isTitleMatch = existingTitleTrimmed.equals(newTitle, ignoreCase = true)

            val originalTitleValue = originalTitle
            val isCurrentMessage = if (originalTitleValue != null) {
                val originalTitleTrimmed = originalTitleValue.trim()
                originalTitleTrimmed.equals(originalTitleTrimmed, ignoreCase = true) &&
                        (existingMessage.isDefault == isDefaultMessage)
            } else {
                false
            }

            isTitleMatch && !isCurrentMessage
        }

        if (hasDuplicate) {
            Snackbar.make(requireView(), "A message with this title already exists", Snackbar.LENGTH_LONG)
                .setAction("OK") { }
                .show()
            return
        }

        val newSystemMessage = SystemMessage(newTitle, newPrompt.trim(), isDefault = isDefaultMessage)


        if (isDefaultMessage) {
            sharedPreferencesHelper.saveDefaultSystemMessage(newSystemMessage)
            val currentSelected = sharedPreferencesHelper.getSelectedSystemMessage()
            if (currentSelected.isDefault) {
                sharedPreferencesHelper.saveSelectedSystemMessage(newSystemMessage)
            }
        } else {
            val customSystemMessages = sharedPreferencesHelper.getCustomSystemMessages().toMutableList()
            if (originalTitle != null) {
                val index = customSystemMessages.indexOfFirst { it.title.equals(originalTitle, ignoreCase = true) }
                if (index != -1) {
                    val currentSelected = sharedPreferencesHelper.getSelectedSystemMessage()
                    if (currentSelected.title.equals(originalTitle, ignoreCase = true)) {
                        sharedPreferencesHelper.saveSelectedSystemMessage(newSystemMessage)
                    }
                    customSystemMessages[index] = newSystemMessage
                }
            } else {
                customSystemMessages.add(newSystemMessage)
            }
            sharedPreferencesHelper.saveCustomSystemMessages(customSystemMessages)
        }
        parentFragmentManager.popBackStack()
    }

}
