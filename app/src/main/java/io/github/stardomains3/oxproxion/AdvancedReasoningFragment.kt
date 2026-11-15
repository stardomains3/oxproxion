package io.github.stardomains3.oxproxion

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

class AdvancedReasoningFragment : Fragment(R.layout.fragment_advanced_reasoning) {

    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private val viewModel: ChatViewModel by activityViewModels()
    private lateinit var effortGroup: MaterialButtonToggleGroup
    private lateinit var includeSwitch: MaterialSwitch
    private lateinit var maxTokensEdit: TextInputEditText

    // Maps generated button IDs -> effort values ("default", "high", "none", etc.)
    private val effortButtonMap = mutableMapOf<Int, String>()

    // Shown when the model publishes no supported_efforts (or is unknown)
    private val fallbackEfforts = listOf("minimal", "low", "medium", "high")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPreferencesHelper = SharedPreferencesHelper(requireContext())

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        // ---- Toolbar advanced on/off toggle ----
        toolbar.inflateMenu(R.menu.advanced_reasoning_menu)
        val menuItem = toolbar.menu.findItem(R.id.menu_advanced_toggle)
        if (menuItem != null) {
            val advancedToggle = menuItem.actionView as MaterialSwitch
            advancedToggle.isChecked = sharedPreferencesHelper.getAdvancedReasoningEnabled()

            val thumbTint = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf("#000000".toColorInt(), "#686868".toColorInt())
            )
            val trackTint = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf("#a0610a".toColorInt(), "#000000".toColorInt())
            )
            advancedToggle.trackTintList = trackTint
            advancedToggle.thumbTintList = thumbTint
            advancedToggle.thumbTintMode = PorterDuff.Mode.SRC_ATOP
            advancedToggle.trackTintMode = PorterDuff.Mode.SRC_ATOP
            advancedToggle.setOnCheckedChangeListener { _, isChecked ->
                sharedPreferencesHelper.saveAdvancedReasoningEnabled(isChecked)
                updateControlsEnabled()
            }
        }

        // ---- Wire up views ----
        effortGroup = view.findViewById(R.id.effortGroup)
        includeSwitch = view.findViewById(R.id.includeSwitch)
        maxTokensEdit = view.findViewById(R.id.maxTokensEdit)

        // ---- Build dynamic effort buttons from the active model's capabilities ----
        val modelId = viewModel.activeChatModel.value
        val model = viewModel.getOpenRouterModelById(modelId)
        val efforts = model?.reasoningEfforts?.takeIf { it.isNotEmpty() } ?: fallbackEfforts
        buildEffortButtons(efforts, model?.reasoningDefaultEffort)

        effortGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                effortButtonMap[checkedId]?.let { effort ->
                    sharedPreferencesHelper.saveReasoningEffort(effort)
                }
            }
        }

        // ---- Include (display) switch ----
        val switchThumbTint = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf("#000000".toColorInt(), "#686868".toColorInt())
        )
        val switchTrackTint = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf("#a0610a".toColorInt(), "#000000".toColorInt())
        )
        includeSwitch.trackTintList = switchTrackTint
        includeSwitch.thumbTintList = switchThumbTint
        includeSwitch.thumbTintMode = PorterDuff.Mode.SRC_ATOP
        includeSwitch.trackTintMode = PorterDuff.Mode.SRC_ATOP
        includeSwitch.isChecked = !sharedPreferencesHelper.getReasoningExclude()
        includeSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferencesHelper.saveReasoningExclude(!isChecked)  // exclude = !include
            viewModel.refreshReasoningVisibility()
        }

        // ---- Max reasoning tokens ----
        maxTokensEdit.setText(sharedPreferencesHelper.getReasoningMaxTokens()?.toString() ?: "")
        maxTokensEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                sharedPreferencesHelper.saveReasoningMaxTokens(s?.toString()?.toIntOrNull())
                updateControlsEnabled()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // ---- Apply initial enabled state (single source of truth) ----
        updateControlsEnabled()
    }

    /**
     * Builds the effort buttons: "Default" first, then the model's supported efforts.
     * Restores the stored selection; if the stored effort isn't valid for this model,
     * visually selects the model's default_effort (or "Default").
     * The stored pref is NOT rewritten here — resolveReasoningEffort() in the
     * ViewModel handles invalid values at request time.
     */
    private fun buildEffortButtons(efforts: List<String>, defaultEffort: String?) {
        effortGroup.removeAllViews()
        effortButtonMap.clear()

        val entries = mutableListOf<Pair<String, String>>() // value -> label
        entries.add("default" to "Default")
        efforts.forEach { effort ->
            entries.add(effort to effort.replaceFirstChar { it.uppercase() })
        }

        entries.forEach { (value, label) ->
            val button = MaterialButton(
                requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
            )
            button.id = View.generateViewId()
            button.text = label
            // Full-width buttons (toggle group is a LinearLayout subclass)
            button.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            effortButtonMap[button.id] = value
            effortGroup.addView(button)
        }

        // Restore selection
        val stored = sharedPreferencesHelper.getReasoningEffort()
        val selected = when {
            stored == "default" -> "default"
            stored in efforts -> stored
            defaultEffort != null && defaultEffort in efforts -> defaultEffort
            else -> "default"
        }
        effortButtonMap.entries.firstOrNull { it.value == selected }?.key?.let {
            effortGroup.check(it)
        }
    }

    /**
     * Single source of truth for control enablement. Every state change
     * (toolbar toggle, token text, fragment open) funnels through here.
     *
     * Effort buttons require ALL of:
     *  - Advanced Reasoning toggle ON
     *  - No max reasoning tokens set (a token value overrides effort)
     *  - The active model actually uses effort (OpenRouter models, or Ollama LAN;
     *    other LAN providers only use a thinking on/off boolean)
     */
    private fun updateControlsEnabled() {
        val advancedOn = sharedPreferencesHelper.getAdvancedReasoningEnabled()

        val maxTokens = sharedPreferencesHelper.getReasoningMaxTokens()
        val effortAllowedByTokens = maxTokens == null || maxTokens <= 0

        val modelId = viewModel.activeChatModel.value
        val isLan = viewModel.isLanModel(modelId)
        val provider = viewModel.getCurrentLanProvider()
        val effortRelevant = !(isLan && provider != SharedPreferencesHelper.LAN_PROVIDER_OLLAMA)

        val effortEnabled = advancedOn && effortAllowedByTokens && effortRelevant

        effortGroup.isEnabled = effortEnabled
        for (i in 0 until effortGroup.childCount) {
            val button = effortGroup.getChildAt(i) as MaterialButton
            button.isEnabled = effortEnabled
            button.isClickable = effortEnabled
        }

        includeSwitch.isEnabled = advancedOn
        includeSwitch.isClickable = advancedOn
        maxTokensEdit.isEnabled = advancedOn
        view?.findViewById<android.widget.TextView>(R.id.effortLabel)?.text =
            if (effortRelevant) "Effort Level:"
            else "Effort Level (not used by this LAN provider):"

    }
}
