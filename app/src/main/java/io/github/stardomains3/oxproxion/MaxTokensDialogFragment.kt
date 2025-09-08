package io.github.stardomains3.oxproxion

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import androidx.core.graphics.drawable.toDrawable

class MaxTokensDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "MaxTokensDialogFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_save_maxtokens, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog?.window?.setDimAmount(0.8f)
        val sharedPreferencesHelper = SharedPreferencesHelper(requireContext())
        val editTextMaxTokens = view.findViewById<TextInputEditText>(R.id.edit_text_maxtokens)
        val buttonSave = view.findViewById<MaterialButton>(R.id.button_savemaxtokens)
        val buttonCancel = view.findViewById<MaterialButton>(R.id.button_cancelmaxtokens)

        // Populate the input with the saved max tokens value
        val savedMaxTokens = sharedPreferencesHelper.getMaxTokens()
        editTextMaxTokens.setText(savedMaxTokens)

        buttonSave.setOnClickListener {
            val maxTokensStr = editTextMaxTokens.text.toString().trim()
            val maxTokensInt = maxTokensStr.toIntOrNull()
            if (maxTokensStr.isNotBlank() && maxTokensInt != null && maxTokensInt in 1..999999) {
                sharedPreferencesHelper.saveMaxTokens(maxTokensStr)
                Toast.makeText(requireContext(), "Max tokens saved.", Toast.LENGTH_SHORT).show()
                dismiss()
            } else {
                editTextMaxTokens.error = "Please enter a number between 1 and 999,999"
            }
        }


        buttonCancel.setOnClickListener {
            dismiss()
        }

        // Request focus and show keyboard automatically
        editTextMaxTokens.requestFocus()

        // dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }
}