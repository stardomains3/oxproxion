package io.github.stardomains3.oxproxion

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlin.text.toIntOrNull
import kotlin.text.trim

class TimeoutDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "TimeoutDialogFragment"
        private const val MIN_MINUTES = 1
        private const val MAX_MINUTES = 45   // you said at least 33, 45 gives a comfortable ceiling
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.dialog_timeout, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // dim + rounded background like MaxTokensDialogFragment
        dialog?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog?.window?.setDimAmount(0.8f)

        val prefs = SharedPreferencesHelper(requireContext())
        val editText = view.findViewById<TextInputEditText>(R.id.edit_text_timeout)
        val btnSave = view.findViewById<MaterialButton>(R.id.button_save_timeout)
        val btnCancel = view.findViewById<MaterialButton>(R.id.button_cancel_timeout)

        // pre‑fill with current value
        editText.setText(prefs.getTimeoutMinutes().toString())

        btnSave.setOnClickListener {
            val txt = editText.text?.toString()?.trim() ?: ""
            val minutes = txt.toIntOrNull()
            if (minutes != null && minutes in MIN_MINUTES..MAX_MINUTES) {
                prefs.saveTimeoutMinutes(minutes)
                Toast.makeText(requireContext(), "Timeout saved ($minutes min)", Toast.LENGTH_SHORT).show()
                dismiss()
            } else {
                editText.error = "Enter a whole number between $MIN_MINUTES and $MAX_MINUTES"
            }
        }

        btnCancel.setOnClickListener { dismiss() }

        // show numeric keypad automatically
        editText.requestFocus()
        // optional: force soft‑keyboard
        // dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }
}