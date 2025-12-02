package io.github.stardomains3.oxproxion

import android.graphics.Color
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

        dialog?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        dialog?.window?.setDimAmount(0.8f)

        val prefs = SharedPreferencesHelper(requireContext())
        val editTextUrl = view.findViewById<TextInputEditText>(R.id.edit_text_lan_url)
        val editTextApiKey = view.findViewById<TextInputEditText>(R.id.edit_text_lan_api_key)
        val checkboxOllama = view.findViewById<CheckBox>(R.id.checkbox_ollama)
        val checkboxLmStudio = view.findViewById<CheckBox>(R.id.checkbox_lm_studio)
        val checkboxLlamaCpp = view.findViewById<CheckBox>(R.id.checkbox_llama_cpp)
        val checkboxMlxLm = view.findViewById<CheckBox>(R.id.checkbox_mlx_lm)
        val btnSave = view.findViewById<MaterialButton>(R.id.button_save_lan)
        val btnCancel = view.findViewById<MaterialButton>(R.id.button_cancel_lan)

        // Load current values - SIMPLE
        prefs.getLanEndpoint()?.let { editTextUrl.setText(it) }
        editTextApiKey.setText(prefs.getLanApiKey())  // Shows dummy or saved key

        val currentProvider = prefs.getLanProvider()
        when (currentProvider) {
            SharedPreferencesHelper.LAN_PROVIDER_OLLAMA -> checkboxOllama.isChecked = true
            SharedPreferencesHelper.LAN_PROVIDER_LM_STUDIO -> checkboxLmStudio.isChecked = true
            SharedPreferencesHelper.LAN_PROVIDER_LLAMA_CPP -> checkboxLlamaCpp.isChecked = true
            SharedPreferencesHelper.LAN_PROVIDER_MLX_LM -> checkboxMlxLm.isChecked = true
        }

        // Checkbox mutual exclusion
        checkboxOllama.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkboxLmStudio.isChecked = false
                checkboxLlamaCpp.isChecked = false
                checkboxMlxLm.isChecked = false
            }
        }
        checkboxLmStudio.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkboxOllama.isChecked = false
                checkboxLlamaCpp.isChecked = false
                checkboxMlxLm.isChecked = false
            }
        }
        checkboxLlamaCpp.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkboxOllama.isChecked = false
                checkboxLmStudio.isChecked = false
                checkboxMlxLm.isChecked = false
            }
        }
        checkboxMlxLm.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkboxOllama.isChecked = false
                checkboxLmStudio.isChecked = false
                checkboxLlamaCpp.isChecked = false
            }
        }

        btnSave.setOnClickListener {
            val raw = editTextUrl.text?.toString()?.trim().orEmpty()
            val apiKey = editTextApiKey.text?.toString()?.trim()

            when {
                raw.isBlank() -> {
                    editTextUrl.error = "Please enter a LAN endpoint URL"
                }
                !checkboxOllama.isChecked && !checkboxLmStudio.isChecked && !checkboxLlamaCpp.isChecked && !checkboxMlxLm.isChecked -> {
                    Toast.makeText(requireContext(), "Please select Ollama, LM Studio, llama.cpp, or MLX LM", Toast.LENGTH_SHORT).show()
                }
                raw.startsWith("http://") || raw.startsWith("https://") || raw.contains("://") -> {
                    prefs.setLanEndpoint(raw)
                    prefs.setLanApiKey(apiKey)  // SIMPLE - stores dummy if blank

                    val provider = when {
                        checkboxOllama.isChecked -> SharedPreferencesHelper.LAN_PROVIDER_OLLAMA
                        checkboxLmStudio.isChecked -> SharedPreferencesHelper.LAN_PROVIDER_LM_STUDIO
                        checkboxLlamaCpp.isChecked -> SharedPreferencesHelper.LAN_PROVIDER_LLAMA_CPP
                        checkboxMlxLm.isChecked -> SharedPreferencesHelper.LAN_PROVIDER_MLX_LM
                        else -> SharedPreferencesHelper.LAN_PROVIDER_OLLAMA
                    }
                    prefs.setLanProvider(provider)

                    Toast.makeText(requireContext(), "LAN endpoint, provider, and API key saved", Toast.LENGTH_SHORT).show()
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


