package io.github.stardomains3.oxproxion
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import androidx.activity.OnBackPressedCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LanSettingsFragment : Fragment(R.layout.fragment_settings_lan) {

    private val viewModel: ChatViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val prefs = SharedPreferencesHelper(requireContext())
        val scrollView = view.findViewById<ScrollView>(R.id.lanScroll)
        val urlLayout = view.findViewById<TextInputLayout>(R.id.lanUrlInputLayout)
        val editTextUrl = view.findViewById<TextInputEditText>(R.id.edit_text_lan_url)
        val editTextApiKey = view.findViewById<TextInputEditText>(R.id.edit_text_lan_api_key)
        val radioGroup = view.findViewById<RadioGroup>(R.id.providerRadioGroup)
        val tlsSection = view.findViewById<View>(R.id.tlsSection)
        val tlsSwitch = view.findViewById<MaterialSwitch>(R.id.insecureLanTlsSwitch)
        val btnSave = view.findViewById<MaterialButton>(R.id.button_save_lan)

        // ---- Load current values ----
        val initialUrl = prefs.getLanEndpoint().orEmpty().trim()
        val initialApiKey = prefs.getLanApiKey().trim()
        val initialTls = prefs.getAllowInsecureLanTls()

        editTextUrl.setText(initialUrl)
        editTextApiKey.setText(initialApiKey)
        tlsSwitch.isChecked = initialTls

        val providerToRadio = mapOf(
            SharedPreferencesHelper.LAN_PROVIDER_OLLAMA to R.id.radio_ollama,
            SharedPreferencesHelper.LAN_PROVIDER_LM_STUDIO to R.id.radio_lm_studio,
            SharedPreferencesHelper.LAN_PROVIDER_LLAMA_CPP to R.id.radio_llama_cpp,
            SharedPreferencesHelper.LAN_PROVIDER_MLX_LM to R.id.radio_mlx_lm,
            SharedPreferencesHelper.LAN_PROVIDER_HERMES_AGENT to R.id.radio_hermes_agent,
            SharedPreferencesHelper.LAN_PROVIDER_OMLX to R.id.radio_olmx,
            SharedPreferencesHelper.LAN_PROVIDER_NATIV to R.id.radio_nativ
        )
        val initialRadioId = providerToRadio[prefs.getLanProvider()] ?: -1
        if (initialRadioId != -1) radioGroup.check(initialRadioId)
// ---- https detection: pulse + scroll to the TLS toggle ----
// Only fire on the transition to https so it doesn't re-trigger
// while the user keeps typing in the URL field.
        var wasHttps = editTextUrl.text?.toString()?.trim()
            ?.startsWith("https://", ignoreCase = true) == true

        editTextUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val isHttps = s?.toString()?.trim()?.startsWith("https://", ignoreCase = true) == true

                if (isHttps && !wasHttps) {
                    // Pulse the section so the eye lands on it
                    tlsSection.setBackgroundColor(Color.argb(255, 90, 60, 20))
                    ObjectAnimator.ofArgb(
                        tlsSection, "backgroundColor",
                        Color.argb(255, 90, 60, 20), Color.argb(255, 44, 44, 46)
                    ).apply {
                        duration = 1400
                        startDelay = 400
                        start()
                    }

                    // Scroll it into view (post so layout pass completes first)
                    scrollView.post {
                        tlsSection.requestRectangleOnScreen(
                            android.graphics.Rect(0, 0, tlsSection.width, tlsSection.height),
                            true
                        )
                    }
                }
                wasHttps = isHttps
            }
        })




        // ---- Save ----
        btnSave.setOnClickListener {
            val raw = editTextUrl.text?.toString()?.trim().orEmpty()
            val apiKey = editTextApiKey.text?.toString()?.trim()
            when {
                raw.isBlank() -> urlLayout.error = "Please enter a LAN endpoint URL"
                !raw.startsWith("http://") && !raw.startsWith("https://") ->
                    urlLayout.error = "URL must contain a scheme (e.g. http:// or https://)"
                radioGroup.checkedRadioButtonId == -1 ->
                    Toast.makeText(requireContext(), "Please select a server type", Toast.LENGTH_SHORT).show()
                else -> {
                    urlLayout.error = null
                    prefs.setLanEndpoint(raw)
                    prefs.setLanApiKey(apiKey)
                    prefs.saveAllowInsecureLanTls(tlsSwitch.isChecked)

                    val provider = when (radioGroup.checkedRadioButtonId) {
                        R.id.radio_lm_studio -> SharedPreferencesHelper.LAN_PROVIDER_LM_STUDIO
                        R.id.radio_llama_cpp -> SharedPreferencesHelper.LAN_PROVIDER_LLAMA_CPP
                        R.id.radio_mlx_lm -> SharedPreferencesHelper.LAN_PROVIDER_MLX_LM
                        R.id.radio_hermes_agent -> SharedPreferencesHelper.LAN_PROVIDER_HERMES_AGENT
                        R.id.radio_olmx -> SharedPreferencesHelper.LAN_PROVIDER_OMLX
                        R.id.radio_nativ -> SharedPreferencesHelper.LAN_PROVIDER_NATIV
                        else -> SharedPreferencesHelper.LAN_PROVIDER_OLLAMA
                    }
                    prefs.setLanProvider(provider)

                    // Rebuild the HTTP clients so the new TLS setting takes effect
                    viewModel.refreshHttpClient()

                    Toast.makeText(requireContext(), "LAN settings saved", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                }
            }
        }

        styleAllSwitches(view, R.id.insecureLanTlsSwitch)
        // ---- Unsaved changes check ----
        fun hasUnsavedChanges(): Boolean {
            val currentUrl = editTextUrl.text?.toString()?.trim().orEmpty()
            val currentApiKey = editTextApiKey.text?.toString()?.trim().orEmpty()
            return currentUrl != initialUrl ||
                    currentApiKey != initialApiKey ||
                    tlsSwitch.isChecked != initialTls ||
                    radioGroup.checkedRadioButtonId != initialRadioId
        }
        fun handleBackNavigation() {
            if (!hasUnsavedChanges()) {
                parentFragmentManager.popBackStack()
                return
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Discard changes?")
                .setMessage("You have unsaved LAN settings. Are you sure you want to leave?")
                .setPositiveButton("Discard") { _, _ ->
                    parentFragmentManager.popBackStack()
                }
                .setNegativeButton("Keep Editing", null)
                .show()
        }

// Intercept toolbar back arrow
        toolbar.setNavigationOnClickListener { handleBackNavigation() }

// Intercept system back button / predictive back gesture
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackNavigation()
                }
            }
        )
    }
}