package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar

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
                    val newTitle = titleEditText.text.toString()
                    val newPrompt = promptEditText.text.toString()

                    if (newTitle.isNotBlank() && newPrompt.isNotBlank()) {
                        val newSystemMessage = SystemMessage(newTitle, newPrompt, isDefault = isDefaultMessage)

                        if (isDefaultMessage) {
                            // Save as the new default system message
                            sharedPreferencesHelper.saveDefaultSystemMessage(newSystemMessage)

                            // Update selected message if it was the default
                            val currentSelected = sharedPreferencesHelper.getSelectedSystemMessage()
                            if (currentSelected.isDefault) {
                                sharedPreferencesHelper.saveSelectedSystemMessage(newSystemMessage)
                            }
                        } else {
                            // Handle custom messages as before
                            val customSystemMessages = sharedPreferencesHelper.getCustomSystemMessages().toMutableList()
                            if (originalTitle != null) {
                                val index = customSystemMessages.indexOfFirst { it.title == originalTitle }
                                if (index != -1) {
                                    val currentSelected = sharedPreferencesHelper.getSelectedSystemMessage()
                                    if (currentSelected.title == originalTitle) {
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
                    true
                }
                else -> false
            }
        }

    }
}
