package io.github.stardomains3.oxproxion

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.orbitalsonic.soniccolorpicker.views.ColorPickerView
import com.orbitalsonic.soniccolorpicker.picker.ColorPickerPopup

class DisplaySettingsFragment : Fragment(R.layout.fragment_settings_display) {
    private lateinit var prefs: SharedPreferencesHelper
    private lateinit var amoledContainer: View
    private lateinit var amoledSwitch: MaterialSwitch
    private lateinit var aiColorButton: MaterialButton      // <-- add
    private lateinit var userColorButton: MaterialButton
    private lateinit var aiTextColorButton: MaterialButton
    private lateinit var userTextColorButton: MaterialButton
    private val DEFAULT_AI_DAY = 0xFFFFFFFF.toInt()
    private val DEFAULT_AI_NIGHT = 0xFF2C2C2C.toInt()
    private val DEFAULT_USER_DAY = 0xFFEAEAEA.toInt()
    private val DEFAULT_USER_NIGHT = 0xFF222f3d.toInt()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        aiColorButton = view.findViewById(R.id.aiColorButton)
        userColorButton = view.findViewById(R.id.userColorButton)

        val aiDefaultButton = view.findViewById<MaterialButton>(R.id.aiColorDefaultButton)
        val userDefaultButton = view.findViewById<MaterialButton>(R.id.userColorDefaultButton)
        aiTextColorButton = view.findViewById(R.id.aiTextColorButton)
        userTextColorButton = view.findViewById(R.id.userTextColorButton)

        val aiTextDefaultButton = view.findViewById<MaterialButton>(R.id.aiTextColorDefaultButton)
        val userTextDefaultButton = view.findViewById<MaterialButton>(R.id.userTextColorDefaultButton)

        prefs = SharedPreferencesHelper(requireContext())
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
        // in onViewCreated:
        amoledContainer = view.findViewById(R.id.amoledContainer)
        amoledSwitch = view.findViewById<MaterialSwitch>(R.id.amoledSwitch).apply {
            isChecked = prefs.isAmoledModeEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                prefs.setAmoledModeEnabled(isChecked)
                requireActivity().recreate() // re-apply theme everywhere
            }
        }
//val readingFocusDimSwitch = view.findViewById<MaterialSwitch>(R.id.readingFocusDimSwitch)


        // ---- Load current state ----
        when (prefs.getThemeMode()) {
            SharedPreferencesHelper.THEME_LIGHT -> themeToggleGroup.check(R.id.btnThemeLight)
            SharedPreferencesHelper.THEME_DARK  -> themeToggleGroup.check(R.id.btnThemeDark)
            else                                -> themeToggleGroup.check(R.id.btnThemeSystem)
        }
        preferFullscreenChatSwitch.isChecked = prefs.getPreferFullscreenChat()
        floatingTranscribeSwitch.isChecked = prefs.getFloatingTranscribeEnabled()
        keepScreenOnSwitch.isChecked = prefs.getKeepScreenOnPreference()

        aiColorButton.setOnClickListener {
            val ctx = requireContext()
            val initial = prefs.getAiBubbleColor(ctx) ?: defaultFor(ctx, isAi = true)
            showColorPickerDialog("AI Bubble Color", initial) { prefs.setAiBubbleColor(ctx, it) }
        }
        userColorButton.setOnClickListener {
            val ctx = requireContext()
            val initial = prefs.getUserBubbleColor(ctx) ?: defaultFor(ctx, isAi = false)
            showColorPickerDialog("User Bubble Color", initial) { prefs.setUserBubbleColor(ctx, it) }
        }
        aiDefaultButton.setOnClickListener {
            prefs.resetAiBubbleColor(requireContext())
            // updateColorSwatches()
        }
        userDefaultButton.setOnClickListener {
            prefs.resetUserBubbleColor(requireContext())
            //updateColorSwatches()
        }
        aiTextColorButton.setOnClickListener {
            val ctx = requireContext()
            val initial = prefs.getAiTextColor(ctx) ?: resolveTextColorPrimary(ctx)
            showColorPickerDialog("AI Text Color", initial) { prefs.setAiTextColor(ctx, it) }
        }
        userTextColorButton.setOnClickListener {
            val ctx = requireContext()
            val initial = prefs.getUserTextColor(ctx) ?: resolveTextColorPrimary(ctx)
            showColorPickerDialog("User Text Color", initial) { prefs.setUserTextColor(ctx, it) }
        }
        aiTextDefaultButton.setOnClickListener {
            prefs.resetAiTextColor(requireContext())

        }
        userTextDefaultButton.setOnClickListener {
            prefs.resetUserTextColor(requireContext())

        }

        // readingFocusDimSwitch.isChecked = prefs.getReadingFocusDimEnabled()

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
        view.findViewById<View>(R.id.themeInfoIcon).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("About theme changes")
                .setMessage(
                    "Switching theme or AMOLED restarts the screen. " +
                            "Best done when:\n\n" +
                            "• No message is incoming\n" +
                            "• TTS is not playing\n" +
                            "• No tool approval is pending"
                )
                .setPositiveButton("Got it", null)
                .show()
        }




        /* readingFocusDimSwitch.setOnCheckedChangeListener { _, isChecked ->
             prefs.saveReadingFocusDimEnabled(isChecked)
         }*/


        // ---- Font: pop back to chat, then show the font dialog there ----
        fontButton.setOnClickListener {
            requestChatUi("font_dialog")
        }

        // ---- Font Size: pop back to chat, then show the +/- controls there ----
        fontSizeButton.setOnClickListener {
            requestChatUi("font_size")
        }
        updateAmoledVisibility()

        // ---- Style switches ----
        styleAllSwitches(
            view,
            R.id.preferFullscreenChatSwitch,
            R.id.floatingTranscribeSwitch,
            R.id.keepScreenOnSwitch,
            R.id.autoBackSwitch,
            R.id.amoledSwitch
            //   R.id.readingFocusDimSwitch
        )
        // updateColorSwatches()
    }
    override fun onResume() {
        super.onResume()
        view?.findViewById<MaterialButton>(R.id.fontButton)?.text =
            prettyFontName(prefs.getSelectedFont())
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
    // Show only when the *effective* theme is dark
    private fun updateAmoledVisibility() {
        val themeMode = prefs.getThemeMode()
        val systemDark = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val effectiveDark = when (themeMode) {
            SharedPreferencesHelper.THEME_DARK -> true
            SharedPreferencesHelper.THEME_LIGHT -> false
            else -> systemDark
        }
        amoledContainer.visibility = if (effectiveDark) View.VISIBLE else View.GONE
    }

    // "geologica_light" → "Geologica light" (good enough for a label)
    private fun prettyFontName(stored: String?): String {
        val name = stored?.takeIf { it.isNotBlank() } ?: "geologica_light"
        return name.replace("_", " ").replaceFirstChar { it.uppercase() }
    }
    private fun showColorPickerDialog(title: String, initialColor: Int, onSave: (Int) -> Unit) {
        val ctx = requireContext()
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val picker = dialogView.findViewById<ColorPickerView>(R.id.colorPicker)
        val preview = dialogView.findViewById<View>(R.id.previewBubble)
        val hexLabel = dialogView.findViewById<TextView>(R.id.previewHexLabel)

        var picked = initialColor   // committed only on "Set Color"

        val updatePreview = { color: Int ->
            preview.background = previewDrawable(ctx, color)
            hexLabel.text = "#%06X".format(0xFFFFFF and color)
        }
        updatePreview(initialColor)

        picker.setInitialColor(initialColor)
        picker.subscribe(object : ColorPickerPopup.ColorPickerObserver() {
            override fun onColor(color: Int, fromUser: Boolean, shouldPropagate: Boolean) {
                picked = color
                updatePreview(color)
            }
            override fun onColorPicked(color: Int) { picked = color }
            override fun onCancel() { /* Cancel button handles dismissal */ }
        })

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setView(dialogView)
            .create()

        dialogView.findViewById<MaterialButton>(R.id.cancelColorButton).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<MaterialButton>(R.id.setColorButton).setOnClickListener {
            onSave(picked)
            //   updateColorSwatches()
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun previewDrawable(context: Context, color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = 16f * context.resources.displayMetrics.density
        }
    private fun defaultFor(ctx: Context, isAi: Boolean): Int {
        val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        return when {
            isAi && night -> DEFAULT_AI_NIGHT
            isAi -> DEFAULT_AI_DAY
            night -> DEFAULT_USER_NIGHT
            else -> DEFAULT_USER_DAY
        }
    }

    private fun resolveTextColorPrimary(context: Context): Int {
        val tv = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
        return when {
            // Attr resolved to a plain color — use it directly
            tv.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT &&
                    tv.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT -> tv.data

            // Attr resolved to a ColorStateList resource — get its default color
            tv.resourceId != 0 -> ContextCompat.getColor(context, tv.resourceId)

            // Shouldn't happen, but don't crash on it
            else -> Color.BLACK
        }
    }


    private fun isSystemDark() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES



}