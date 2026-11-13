package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch

class DisplaySettingsFragment : Fragment(R.layout.fragment_settings_display) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val prefs = SharedPreferencesHelper(requireContext())

        val themeToggleGroup =
            view.findViewById<MaterialButtonToggleGroup>(R.id.themeToggleGroup)
        val preferFullscreenChatSwitch =
            view.findViewById<MaterialSwitch>(R.id.preferFullscreenChatSwitch)
        val floatingTranscribeSwitch =
            view.findViewById<MaterialSwitch>(R.id.floatingTranscribeSwitch)
        val keepScreenOnSwitch =
            view.findViewById<MaterialSwitch>(R.id.keepScreenOnSwitch)

        // ---- Load current state ----
        when (prefs.getThemeMode()) {
            SharedPreferencesHelper.THEME_LIGHT -> themeToggleGroup.check(R.id.btnThemeLight)
            SharedPreferencesHelper.THEME_DARK  -> themeToggleGroup.check(R.id.btnThemeDark)
            else                                -> themeToggleGroup.check(R.id.btnThemeSystem)
        }
        preferFullscreenChatSwitch.isChecked = prefs.getPreferFullscreenChat()
        floatingTranscribeSwitch.isChecked = prefs.getFloatingTranscribeEnabled()
        keepScreenOnSwitch.isChecked = prefs.getKeepScreenOnPreference()

        // ---- Theme Mode (verbatim from your original) ----
        themeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) { // Only trigger when a button is selected
                val mode = when (checkedId) {
                    R.id.btnThemeLight -> SharedPreferencesHelper.THEME_LIGHT
                    R.id.btnThemeDark  -> SharedPreferencesHelper.THEME_DARK
                    else               -> SharedPreferencesHelper.THEME_SYSTEM
                }

                // Save the choice
                prefs.saveThemeMode(mode)

                // Determine the AppCompatDelegate mode
                val appMode = when (mode) {
                    SharedPreferencesHelper.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    SharedPreferencesHelper.THEME_DARK  -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }

                // Apply and Recreate
                if (AppCompatDelegate.getDefaultNightMode() != appMode) {
                    AppCompatDelegate.setDefaultNightMode(appMode)
                    requireActivity().recreate()
                }
            }
        }

        // ---- Prefer Fullscreen Chat ----
        preferFullscreenChatSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.savePreferFullscreenChat(isChecked)
        }

        // ---- Floating Immersive View Toggle ----
        floatingTranscribeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveFloatingTranscribeEnabled(isChecked)
        }

        // ---- Keep Screen On (verbatim, incl. live window flag) ----
        keepScreenOnSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveKeepScreenOnPreference(isChecked)

            val window = requireActivity().window
            if (isChecked) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        // ---- Style switches ----
        styleAllSwitches(
            view,
            R.id.preferFullscreenChatSwitch,
            R.id.floatingTranscribeSwitch,
            R.id.keepScreenOnSwitch
        )
    }
}
