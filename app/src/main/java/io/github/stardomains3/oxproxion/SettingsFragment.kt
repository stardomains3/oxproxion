package io.github.stardomains3.oxproxion

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        val prefs = SharedPreferencesHelper(requireContext())

        val viewModel: ChatViewModel by activityViewModels()
        val biometricsSwitch = view.findViewById<MaterialSwitch>(R.id.biometricsSwitch)
        val autoDisableWebSearchSwitch = view.findViewById<MaterialSwitch>(R.id.autoDisableWebSearchSwitch)
        val notificationsSwitch = view.findViewById<MaterialSwitch>(R.id.notificationsSwitch)
        val keepScreenOnSwitch = view.findViewById<MaterialSwitch>(R.id.keepScreenOnSwitch)
        val scrollButtonsSwitch = view.findViewById<MaterialSwitch>(R.id.scrollButtonsSwitch)
        val extendedDockSwitch = view.findViewById<MaterialSwitch>(R.id.extendedDockSwitch)
        val presetsExtendedSwitch = view.findViewById<MaterialSwitch>(R.id.presetsExtendedSwitch)
        val scrollProgressSwitch = view.findViewById<MaterialSwitch>(R.id.scrollProgressSwitch)
        val apiKeyButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.apiKeyButton)
        val promptsButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.promptsButton)
        val creditsButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.creditsButton)
        val helpButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.helpButton)
        val maxTokensButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.maxTokensButton)
        val lanButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.lanButton)
        biometricsSwitch.isChecked = prefs.getBiometricEnabled()
        notificationsSwitch.isChecked = prefs.getNotiPreference()
        autoDisableWebSearchSwitch.isChecked = prefs.getDisableWebSearchAfterSend()
        keepScreenOnSwitch.isChecked = prefs.getKeepScreenOnPreference()
        scrollButtonsSwitch.isChecked = viewModel.isScrollersEnabled.value ?: false
        extendedDockSwitch.isChecked = viewModel.isExtendedDockEnabled.value ?: false
        presetsExtendedSwitch.isChecked = viewModel.isPresetsExtendedEnabled.value ?: false
        scrollProgressSwitch.isChecked = viewModel.isScrollProgressEnabled.value ?: true
        apiKeyButton.setOnClickListener {
            val dialog = SaveApiDialogFragment()
            dialog.show(childFragmentManager, "SaveApiDialogFragment")
        }
        promptsButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .hide(this)
                .add(R.id.fragment_container, PromptLibraryFragment())
                .addToBackStack(null)
                .commit()
        }
        extendedDockSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleExtendedDock()  // VM saves + notifies Chat
        }
        presetsExtendedSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.togglePresetsExtended()  // VM saves + notifies Chat
        }
        viewModel.isPresetsExtendedEnabled.observe(viewLifecycleOwner) { enabled ->
            presetsExtendedSwitch.isChecked = enabled
        }
        autoDisableWebSearchSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveDisableWebSearchAfterSend(isChecked)
        }
        notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveNotiPreference(isChecked)
        }
        scrollProgressSwitch.setOnCheckedChangeListener { _, isChecked -> viewModel.toggleScrollProgress() }
        viewModel.isScrollProgressEnabled.observe(viewLifecycleOwner) { enabled -> scrollProgressSwitch.isChecked = enabled }
        creditsButton.setOnClickListener {
            if (viewModel.activeChatApiKey.isBlank()) {
                Toast.makeText(requireContext(), "API Key is not set.", Toast.LENGTH_SHORT).show()
            } else {
                parentFragmentManager.popBackStack()
                viewModel.checkRemainingCredits()
            }
        }
        scrollButtonsSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleScrollers()  // 🔥 VM saves prefs + notifies Chat instantly
        }
        keepScreenOnSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveKeepScreenOnPreference(isChecked)  // Save forever

            val window = requireActivity().window
            if (isChecked) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        helpButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .hide(this)
                .add(R.id.fragment_container, HelpFragment())
                .addToBackStack(null)
                .commit()
        }
        maxTokensButton.setOnClickListener {
            val dialog = MaxTokensDialogFragment()
            dialog.show(childFragmentManager, "MaxTokensDialogFragment")
        }
        lanButton.setOnClickListener {
            SaveLANDialogFragment().show(childFragmentManager, SaveLANDialogFragment.TAG)
        }
        biometricsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val bm = BiometricManager.from(requireContext())
                when (bm.canAuthenticate(BIOMETRIC_STRONG)) {
                    BiometricManager.BIOMETRIC_SUCCESS -> {
                        prefs.saveBiometricEnabled(true)
                    }
                    else -> {
                        biometricsSwitch.isChecked = false
                        Toast.makeText(requireContext(), "No biometrics available", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                prefs.saveBiometricEnabled(false)
            }
        }


        // 🔥 STYLE ALL SWITCHES (your exact code → reusable)
        listOf(
            R.id.scrollButtonsSwitch,
            R.id.scrollProgressSwitch,
            R.id.keepScreenOnSwitch,
            R.id.biometricsSwitch,
            R.id.extendedDockSwitch,
            R.id.notificationsSwitch,
            R.id.presetsExtendedSwitch,
            R.id.autoDisableWebSearchSwitch
        ).forEach { id ->
            view.findViewById<MaterialSwitch>(id)?.styleSwitch()
        }
    }

    // 🔥 HELPER: Your exact switch style (call on each)
    private fun MaterialSwitch.styleSwitch() {
        val thumbTintSelector = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                "#000000".toColorInt(),  // Checked: Black thumb
                "#686868".toColorInt()   // Unchecked: Gray thumb
            )
        )
        val trackTintSelector = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                "#a0610a".toColorInt(),  // Checked: Orange track
                "#000000".toColorInt()   // Unchecked: Black track
            )
        )

        trackTintList = trackTintSelector
        thumbTintList = thumbTintSelector
        thumbTintMode = PorterDuff.Mode.SRC_ATOP
        trackTintMode = PorterDuff.Mode.SRC_ATOP
    }
}