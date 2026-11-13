package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
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
        val fontButton = view.findViewById<MaterialButton>(R.id.fontButton)
        val fontSizeButton = view.findViewById<MaterialButton>(R.id.fontSizeButton)
        val readingFocusDimSwitch = view.findViewById<MaterialSwitch>(R.id.readingFocusDimSwitch)


        // ---- Load current state ----
        when (prefs.getThemeMode()) {
            SharedPreferencesHelper.THEME_LIGHT -> themeToggleGroup.check(R.id.btnThemeLight)
            SharedPreferencesHelper.THEME_DARK  -> themeToggleGroup.check(R.id.btnThemeDark)
            else                                -> themeToggleGroup.check(R.id.btnThemeSystem)
        }
        preferFullscreenChatSwitch.isChecked = prefs.getPreferFullscreenChat()
        floatingTranscribeSwitch.isChecked = prefs.getFloatingTranscribeEnabled()
        keepScreenOnSwitch.isChecked = prefs.getKeepScreenOnPreference()
        readingFocusDimSwitch.isChecked = prefs.getReadingFocusDimEnabled()

        fontButton.text = prettyFontName(prefs.getSelectedFont())

        // ---- Theme Mode ----
        themeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val mode = when (checkedId) {
                    R.id.btnThemeLight -> SharedPreferencesHelper.THEME_LIGHT
                    R.id.btnThemeDark  -> SharedPreferencesHelper.THEME_DARK
                    else               -> SharedPreferencesHelper.THEME_SYSTEM
                }
                prefs.saveThemeMode(mode)
                val appMode = when (mode) {
                    SharedPreferencesHelper.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    SharedPreferencesHelper.THEME_DARK  -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
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

        // ---- Keep Screen On ----
        keepScreenOnSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveKeepScreenOnPreference(isChecked)
            val window = requireActivity().window
            if (isChecked) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        readingFocusDimSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveReadingFocusDimEnabled(isChecked)
        }


        // ---- Font: pop back to chat, then show the font dialog there ----
        fontButton.setOnClickListener {
            requestChatUi("font_dialog")
        }

        // ---- Font Size: pop back to chat, then show the +/- controls there ----
        fontSizeButton.setOnClickListener {
            requestChatUi("font_size")
        }

        // ---- Style switches ----
        styleAllSwitches(
            view,
            R.id.preferFullscreenChatSwitch,
            R.id.floatingTranscribeSwitch,
            R.id.keepScreenOnSwitch,
            R.id.readingFocusDimSwitch
        )
    }

    // 🔥 Tell ChatFragment to show something, then unwind to it.
    // Result is delivered synchronously to ChatFragment's listener, so
    // dialog/controls appear as the pops land the user back on the chat.
    private fun requestChatUi(action: String) {
        parentFragmentManager.setFragmentResult(
            ChatFragment.UI_REQUEST_KEY,
            Bundle().apply { putString("action", action) }
        )
        parentFragmentManager.popBackStack() // Display → Settings
        parentFragmentManager.popBackStack() // Settings → Chat
    }

    // "geologica_light" → "Geologica light" (good enough for a label)
    private fun prettyFontName(stored: String?): String {
        val name = stored?.takeIf { it.isNotBlank() } ?: "geologica_light"
        return name.replace("_", " ").replaceFirstChar { it.uppercase() }
    }
}
