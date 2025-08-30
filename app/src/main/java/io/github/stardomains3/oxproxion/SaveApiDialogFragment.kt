package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SaveApiDialogFragment : DialogFragment() {

    private val viewModel: ChatViewModel by activityViewModels()

    companion object {
        const val TAG = "SaveApiDialogFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_save_api, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPreferencesHelper = SharedPreferencesHelper(requireContext())
        val editTextApiKey = view.findViewById<TextInputEditText>(R.id.edit_text_title)
        val buttonSave = view.findViewById<MaterialButton>(R.id.button_saveapi)
        val buttonCancel = view.findViewById<MaterialButton>(R.id.button_cancelapi)

        buttonSave.setOnClickListener {
            val apiKey = editTextApiKey.text.toString().trim()
            if (apiKey.isNotBlank()) {
                sharedPreferencesHelper.saveApiKey("openrouter_api_key", apiKey)
                viewModel.refreshApiKey() // Tell the ViewModel to reload the key
                Toast.makeText(requireContext(), "API Key saved.", Toast.LENGTH_SHORT).show()
                dismiss()
            } else {
                editTextApiKey.error = "API Key cannot be empty"
            }
        }

        buttonCancel.setOnClickListener {
            dismiss()
        }

        // Request focus and show keyboard automatically
        editTextApiKey.requestFocus()
      //  dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }
}
