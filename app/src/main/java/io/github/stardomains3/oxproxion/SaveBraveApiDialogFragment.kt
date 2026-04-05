package io.github.stardomains3.oxproxion

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SaveBraveApiDialogFragment : DialogFragment() {

    private val viewModel: ChatViewModel by activityViewModels()

    companion object {
        const val TAG = "SaveBraveApiDialogFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_save_brave_api, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog?.window?.setDimAmount(0.8f)
        val sharedPreferencesHelper = SharedPreferencesHelper(requireContext())
        val editTextApiKey = view.findViewById<TextInputEditText>(R.id.edit_text_brave_api)
        val buttonSave = view.findViewById<MaterialButton>(R.id.button_save_brave_api)
        val buttonCancel = view.findViewById<MaterialButton>(R.id.button_cancel_brave_api)

        buttonSave.setOnClickListener {
            val apiKey = editTextApiKey.text.toString().trim()
            if (apiKey.isNotBlank()) {
                sharedPreferencesHelper.saveApiKey("brave_search_api_key", apiKey)
                viewModel.refreshApiKey() // Tell the ViewModel to reload the key
                Toast.makeText(requireContext(), "Brave API Key saved.", Toast.LENGTH_SHORT).show()
                dismiss()
            } else {
                editTextApiKey.error = "API Key cannot be empty"
            }
        }

        buttonCancel.setOnClickListener {
            dismiss()
        }

        editTextApiKey.requestFocus()
    }
}
