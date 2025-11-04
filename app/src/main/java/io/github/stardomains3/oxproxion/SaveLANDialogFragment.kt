package io.github.stardomains3.oxproxion

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.CheckBox
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import androidx.core.graphics.drawable.toDrawable
import kotlin.let
import kotlin.text.contains
import kotlin.text.isBlank
import kotlin.text.orEmpty
import kotlin.text.startsWith
import kotlin.text.trim

class SaveLANDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "SaveLANDialogFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_save_lan, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Dialog styling – matches the API dialog
        dialog?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog?.window?.setDimAmount(0.8f)

        val prefs = SharedPreferencesHelper(requireContext())
        val editTextUrl = view.findViewById<TextInputEditText>(R.id.edit_text_lan_url)
        val checkboxOllama = view.findViewById<CheckBox>(R.id.checkbox_ollama)
        val checkboxLmStudio = view.findViewById<CheckBox>(R.id.checkbox_lm_studio)
        val checkboxLlamaCpp = view.findViewById<CheckBox>(R.id.checkbox_llama_cpp)
        val btnSave   = view.findViewById<MaterialButton>(R.id.button_save_lan)
        val btnCancel = view.findViewById<MaterialButton>(R.id.button_cancel_lan)

        // Load current values
        prefs.getLanEndpoint()?.let { editTextUrl.setText(it) }
        val currentProvider = prefs.getLanProvider()
        when (currentProvider) {
            SharedPreferencesHelper.LAN_PROVIDER_OLLAMA -> checkboxOllama.isChecked = true
            SharedPreferencesHelper.LAN_PROVIDER_LM_STUDIO -> checkboxLmStudio.isChecked = true
            SharedPreferencesHelper.LAN_PROVIDER_LLAMA_CPP -> checkboxLlamaCpp.isChecked = true
        }

        // Handle checkbox mutual exclusion
        checkboxOllama.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkboxLmStudio.isChecked = false
                checkboxLlamaCpp.isChecked = false // NEW
            }
        }
        checkboxLmStudio.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkboxOllama.isChecked = false
                checkboxLlamaCpp.isChecked = false // NEW
            }
        }
// NEW: Handle llama.cpp checkbox
        checkboxLlamaCpp.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkboxOllama.isChecked = false
                checkboxLmStudio.isChecked = false
            }
        }

        btnSave.setOnClickListener {
            val raw = editTextUrl.text?.toString()?.trim().orEmpty()

            when {
                raw.isBlank() -> {
                    editTextUrl.error = "Please enter a LAN endpoint URL"
                }
                !checkboxOllama.isChecked && !checkboxLmStudio.isChecked && !checkboxLlamaCpp.isChecked -> { // UPDATED
                    Toast.makeText(requireContext(), "Please select Ollama, LM Studio, or llama.cpp", Toast.LENGTH_SHORT).show() // UPDATED
                }
                raw.startsWith("http://") || raw.startsWith("https://") || raw.contains("://") -> {
                    prefs.setLanEndpoint(raw)

                    // Save provider preference
                    val provider = when { // UPDATED
                        checkboxOllama.isChecked -> SharedPreferencesHelper.LAN_PROVIDER_OLLAMA
                        checkboxLmStudio.isChecked -> SharedPreferencesHelper.LAN_PROVIDER_LM_STUDIO
                        checkboxLlamaCpp.isChecked -> SharedPreferencesHelper.LAN_PROVIDER_LLAMA_CPP // NEW
                        else -> SharedPreferencesHelper.LAN_PROVIDER_OLLAMA
                    }
                    prefs.setLanProvider(provider)

                    Toast.makeText(requireContext(), "LAN endpoint and provider saved", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
                else -> {
                    editTextUrl.error = "URL must contain a scheme (e.g. http:// or https://)"
                }
            }
        }


        btnCancel.setOnClickListener {
            dismiss()
        }

        editTextUrl.requestFocus()
    }
}
