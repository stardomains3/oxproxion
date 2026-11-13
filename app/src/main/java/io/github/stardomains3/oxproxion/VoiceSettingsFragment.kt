package io.github.stardomains3.oxproxion


import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch

class VoiceSettingsFragment : Fragment(R.layout.fragment_settings_voice) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val prefs = SharedPreferencesHelper(requireContext())

        val voiceModelEdit =
            view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.voiceInputModelEdit)
        val voiceProviderToggle =
            view.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.voiceInputProviderToggle)
        val watermarkSttSwitch = view.findViewById<MaterialSwitch>(R.id.watermarkSttSwitch)
        val watermarkSttRow = view.findViewById<View>(R.id.watermarkSttRow)
        val pushToTalkRow = view.findViewById<View>(R.id.pushToTalkRow)
        val pushToTalkSwitch = view.findViewById<MaterialSwitch>(R.id.pushToTalkSwitch)

        // ---- Load current state ----
        voiceModelEdit.setText(prefs.getVoiceInputModel())
        watermarkSttSwitch.isChecked = prefs.getWatermarkSttEnabled()
        pushToTalkSwitch.isChecked = prefs.getPushToTalkEnabled()

        when (prefs.getVoiceInputProvider()) {
            "cloud" -> voiceProviderToggle.check(R.id.providerCloudButton)
            "off"   -> voiceProviderToggle.check(R.id.providerOffButton)
            else    -> voiceProviderToggle.check(R.id.providerLanButton)
        }

        // ---- Visibility logic (unchanged from your original) ----
        fun updateVoiceSettingsVisibility() {
            val hasTranscription = when (prefs.getVoiceInputProvider()) {
                "cloud" -> true
                "lan"   -> prefs.getVoiceInputModel().isNotBlank()
                else    -> false
            }

            pushToTalkRow.visibility = if (hasTranscription) View.VISIBLE else View.GONE
            if (!hasTranscription && prefs.getPushToTalkEnabled()) {
                pushToTalkSwitch.isChecked = false
                prefs.savePushToTalkEnabled(false)
            }

            watermarkSttRow.visibility = if (hasTranscription) View.VISIBLE else View.GONE
            if (!hasTranscription && prefs.getWatermarkSttEnabled()) {
                watermarkSttSwitch.isChecked = false
                prefs.saveWatermarkSttEnabled(false)
            }
        }
        updateVoiceSettingsVisibility()

        // ---- Listeners (unchanged logic, just relocated) ----
        voiceModelEdit.doAfterTextChanged { text ->
            prefs.setVoiceInputModel(text?.toString() ?: "")
            updateVoiceSettingsVisibility()
        }

        voiceProviderToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val provider = when (checkedId) {
                    R.id.providerCloudButton -> "cloud"
                    R.id.providerOffButton   -> "off"
                    else                     -> "lan"
                }
                prefs.setVoiceInputProvider(provider)
                updateVoiceSettingsVisibility()
            }
        }

        watermarkSttSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveWatermarkSttEnabled(isChecked)
        }

        pushToTalkSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.savePushToTalkEnabled(isChecked)
        }

        // ---- Style switches (your exact style) ----
        listOf(R.id.watermarkSttSwitch, R.id.pushToTalkSwitch).forEach { id ->
            view.findViewById<MaterialSwitch>(id)?.styleSwitch()
        }
    }

}
