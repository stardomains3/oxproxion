package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar

class AddEditPromptFragment : Fragment() {

    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private var originalTitle: String? = null

    companion object {
        const val ARG_TITLE = "ARG_TITLE"  // ✅ Standardized to UPPERCASE (matches PromptLibraryFragment putString)
        const val ARG_PROMPT = "ARG_PROMPT"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_edit_prompt, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferencesHelper = SharedPreferencesHelper(requireContext())

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val titleEditText = view.findViewById<EditText>(R.id.edit_text_prompt_title)
        val promptEditText = view.findViewById<EditText>(R.id.edit_text_prompt_prompt)

        originalTitle = arguments?.getString(ARG_TITLE)
        val originalPrompt = arguments?.getString(ARG_PROMPT)

        if (originalTitle != null && originalPrompt != null) {
            // ✅ FIXED: Populate fields for EDIT mode (matches SystemMessage exactly)
            toolbar.title = "Edit Prompt"
            titleEditText.setText(originalTitle)
            promptEditText.setText(originalPrompt)
            // Position cursor at end
            titleEditText.setSelection(titleEditText.text.length)
            promptEditText.setSelection(promptEditText.text.length)
        } else {
            toolbar.title = "Add Prompt"
        }

        //toolbar.inflateMenu(R.menu.add_edit_prompt_menu)  // ✅ Explicit inflate (safe, matches XML app:menu)

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_save_prompt -> {
                    val newTitle = titleEditText.text.toString().trim()
                    val newPrompt = promptEditText.text.toString().trim()

                    if (newTitle.isNotBlank() && newPrompt.isNotBlank()) {
                        // Check for duplicates among custom prompts only
                        val customPrompts = sharedPreferencesHelper.getCustomPrompts()
                        val hasDuplicate = customPrompts.any { existingPrompt ->
                            val existingTitleTrimmed = existingPrompt.title.trim()
                            val isTitleMatch = existingTitleTrimmed.equals(newTitle, ignoreCase = true)
                            val isCurrentPrompt = originalTitle?.trim()?.equals(existingTitleTrimmed, ignoreCase = true) ?: false
                            isTitleMatch && !isCurrentPrompt
                        }

                        if (hasDuplicate) {
                            Snackbar.make(requireView(), "A prompt with this title already exists", Snackbar.LENGTH_LONG)
                                .setAction("OK") { }
                                .show()
                            return@setOnMenuItemClickListener true
                        }

                        // Proceed with saving
                        val newPromptObj = Prompt(newTitle, newPrompt)

                        val updatedPrompts = customPrompts.toMutableList()
                        if (originalTitle != null) {
                            // Edit: replace matching title
                            val index = updatedPrompts.indexOfFirst { it.title.equals(originalTitle!!, ignoreCase = true) }
                            if (index != -1) {
                                updatedPrompts[index] = newPromptObj
                            } else {
                                updatedPrompts.add(newPromptObj)  // Fallback add
                            }
                        } else {
                            // Add new
                            updatedPrompts.add(newPromptObj)
                        }
                        sharedPreferencesHelper.saveCustomPrompts(updatedPrompts)
                        parentFragmentManager.popBackStack()
                    } else {
                        Snackbar.make(requireView(), "Title and prompt cannot be empty", Snackbar.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> false
            }
        }
    }
}