package io.github.stardomains3.oxproxion

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import kotlin.ranges.until
import kotlin.text.toIntOrNull

class AdvancedReasoningFragment : Fragment(R.layout.fragment_advanced_reasoning) {

    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private lateinit var effortGroup: MaterialButtonToggleGroup
    private lateinit var includeSwitch: MaterialSwitch
    private lateinit var maxTokensEdit: TextInputEditText

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPreferencesHelper = SharedPreferencesHelper(requireContext())

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Inflate menu directly on the toolbar
        toolbar.inflateMenu(R.menu.advanced_reasoning_menu)
        val menuItem = toolbar.menu.findItem(R.id.menu_advanced_toggle)
        if (menuItem != null) {
            val advancedToggle = menuItem.actionView as MaterialSwitch // CHANGE THIS LINE
            val isEnabled = sharedPreferencesHelper.getAdvancedReasoningEnabled()
            advancedToggle.isChecked = isEnabled

            val thumbTintSelector = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    "#000000".toColorInt(),  // Checked state color
                    "#686868".toColorInt()   // Unchecked state color
                )
            )
            val trackTintSelector = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    "#a0610a".toColorInt(),  // On state color
                    "#000000".toColorInt()   // Off state color
                )
            )
            advancedToggle.trackTintList = trackTintSelector
            advancedToggle.thumbTintList = thumbTintSelector
            advancedToggle.thumbTintMode = PorterDuff.Mode.SRC_ATOP
            advancedToggle.trackTintMode = PorterDuff.Mode.SRC_ATOP
            advancedToggle.setOnCheckedChangeListener { _, isChecked ->
                sharedPreferencesHelper.saveAdvancedReasoningEnabled(isChecked)
                updateControlsEnabled(isChecked)
            }
        }

        effortGroup = view.findViewById(R.id.effortGroup)
        includeSwitch = view.findViewById(R.id.includeSwitch)
        maxTokensEdit = view.findViewById(R.id.maxTokensEdit)
        val isEnabled = sharedPreferencesHelper.getAdvancedReasoningEnabled()
        updateControlsEnabled(isEnabled)
        loadSettings()
        effortGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                val effort = when (checkedId) {
                    R.id.buttonMinimal -> "minimal"
                    R.id.buttonLow -> "low"
                    R.id.buttonMedium -> "medium"
                    R.id.buttonHigh -> "high"
                    else -> "medium"
                }
                sharedPreferencesHelper.saveReasoningEffort(effort)
            }
        }


        includeSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferencesHelper.saveReasoningExclude(!isChecked)  // exclude = !include
        }

        maxTokensEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val value = s?.toString()?.toIntOrNull()
                sharedPreferencesHelper.saveReasoningMaxTokens(value)
                effortGroup.isEnabled = value == null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val thumbTintSelector = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                "#000000".toColorInt(),  // Checked state color
                "#686868".toColorInt()   // Unchecked state color
            )
        )
        val trackTintSelector = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                "#a0610a".toColorInt(),  // On state color
                "#000000".toColorInt()   // Off state color
            )
        )

// Apply to your MaterialSwitch instance
        includeSwitch.trackTintList = trackTintSelector
        includeSwitch.thumbTintList = thumbTintSelector
        includeSwitch.thumbTintMode = PorterDuff.Mode.SRC_ATOP
        includeSwitch.trackTintMode = PorterDuff.Mode.SRC_ATOP

        val maxTokens = sharedPreferencesHelper.getReasoningMaxTokens()
        effortGroup.isEnabled = maxTokens == null || maxTokens <= 0
    }

    private fun loadSettings() {
        val effort = sharedPreferencesHelper.getReasoningEffort()
        val buttonId = when (effort) {
            "minimal" -> R.id.buttonMinimal
            "low" -> R.id.buttonLow
            "medium" -> R.id.buttonMedium
            "high" -> R.id.buttonHigh
            else -> R.id.buttonMedium
        }
        effortGroup.check(buttonId)

        includeSwitch.isChecked = !sharedPreferencesHelper.getReasoningExclude()
        maxTokensEdit.setText(sharedPreferencesHelper.getReasoningMaxTokens()?.toString() ?: "")
    }

    private fun updateControlsEnabled(enabled: Boolean) {
        effortGroup.isEnabled = enabled
        for (i in 0 until effortGroup.childCount) {
            val button = effortGroup.getChildAt(i) as MaterialButton
            button.isEnabled = enabled
            button.isClickable = enabled
        }
        includeSwitch.isEnabled = enabled
        includeSwitch.isClickable = enabled
        maxTokensEdit.isEnabled = enabled
    }
}
