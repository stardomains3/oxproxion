package io.github.stardomains3.oxproxion

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.core.graphics.toColorInt
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout

class InferenceParametersFragment : Fragment(R.layout.fragment_inference_parameters) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val prefs = SharedPreferencesHelper(requireContext())
// Max Tokens
        setupParameter(
            view = view,
            switchId = R.id.maxTokensSwitch,
            inputLayoutId = R.id.maxTokensInputLayout,
            editId = R.id.maxTokensEdit,
            isEnabled = prefs.getMaxTokensEnabled(),
            currentValue = prefs.getMaxTokens(),
            onSwitchChanged = { prefs.saveMaxTokensEnabled(it) },
            onValueChanged = { text ->
                if (text.toIntOrNull() != null) prefs.saveMaxTokens(text)
            }
        )
        // Temperature
        setupParameter(
            view = view,
            switchId = R.id.tempSwitch,
            inputLayoutId = R.id.tempInputLayout,
            editId = R.id.tempEdit,
            isEnabled = prefs.getInferenceTempEnabled(),
            currentValue = prefs.getInferenceTempValue().toString(),
            onSwitchChanged = { prefs.saveInferenceTempEnabled(it) },
            onValueChanged = { text ->
                // Only save if valid number, else fallback
                if (text.toDoubleOrNull() != null) prefs.saveInferenceTempValue(text)
            }
        )

        // Top P
        setupParameter(
            view = view,
            switchId = R.id.topPSwitch,
            inputLayoutId = R.id.topPInputLayout,
            editId = R.id.topPEdit,
            isEnabled = prefs.getInferenceTopPEnabled(),
            currentValue = prefs.getInferenceTopPValue().toString(),
            onSwitchChanged = { prefs.saveInferenceTopPEnabled(it) },
            onValueChanged = { text ->
                if (text.toDoubleOrNull() != null) prefs.saveInferenceTopPValue(text)
            }
        )

        // Top K
        setupParameter(
            view = view,
            switchId = R.id.topKSwitch,
            inputLayoutId = R.id.topKInputLayout,
            editId = R.id.topKEdit,
            isEnabled = prefs.getInferenceTopKEnabled(),
            currentValue = prefs.getInferenceTopKValue().toString(),
            onSwitchChanged = { prefs.saveInferenceTopKEnabled(it) },
            onValueChanged = { text ->
                if (text.toIntOrNull() != null) prefs.saveInferenceTopKValue(text.toInt())
            }
        )

        // Min P
        setupParameter(
            view = view,
            switchId = R.id.minPSwitch,
            inputLayoutId = R.id.minPInputLayout,
            editId = R.id.minPEdit,
            isEnabled = prefs.getInferenceMinPEnabled(),
            currentValue = prefs.getInferenceMinPValue().toString(),
            onSwitchChanged = { prefs.saveInferenceMinPEnabled(it) },
            onValueChanged = { text ->
                if (text.toDoubleOrNull() != null) prefs.saveInferenceMinPValue(text)
            }
        )

        // Repetition Penalty
        setupParameter(
            view = view,
            switchId = R.id.repPenaltySwitch,
            inputLayoutId = R.id.repPenaltyInputLayout,
            editId = R.id.repPenaltyEdit,
            isEnabled = prefs.getInferenceRepetitionPenaltyEnabled(),
            currentValue = prefs.getInferenceRepetitionPenaltyValue().toString(),
            onSwitchChanged = { prefs.saveInferenceRepetitionPenaltyEnabled(it) },
            onValueChanged = { text ->
                if (text.toDoubleOrNull() != null) prefs.saveInferenceRepetitionPenaltyValue(text)
            }
        )

        // Presence Penalty
        setupParameter(
            view = view,
            switchId = R.id.presPenaltySwitch,
            inputLayoutId = R.id.presPenaltyInputLayout,
            editId = R.id.presPenaltyEdit,
            isEnabled = prefs.getInferencePresencePenaltyEnabled(),
            currentValue = prefs.getInferencePresencePenaltyValue().toString(),
            onSwitchChanged = { prefs.saveInferencePresencePenaltyEnabled(it) },
            onValueChanged = { text ->
                if (text.toDoubleOrNull() != null) prefs.saveInferencePresencePenaltyValue(text)
            }
        )

        // Apply custom switch styling
        listOf(
            R.id.maxTokensSwitch,
            R.id.tempSwitch,
            R.id.topPSwitch,
            R.id.topKSwitch,
            R.id.minPSwitch,
            R.id.repPenaltySwitch,
            R.id.presPenaltySwitch
        ).forEach { id ->
            view.findViewById<MaterialSwitch>(id)?.styleSwitch()
        }
    }

    private fun setupParameter(
        view: View,
        switchId: Int,
        inputLayoutId: Int,
        editId: Int,
        isEnabled: Boolean,
        currentValue: String,
        onSwitchChanged: (Boolean) -> Unit,
        onValueChanged: (String) -> Unit
    ) {
        val switch = view.findViewById<MaterialSwitch>(switchId)
        val inputLayout = view.findViewById<TextInputLayout>(inputLayoutId)
        val edit = view.findViewById<EditText>(editId) // TextInputEditText extends EditText

        switch.isChecked = isEnabled
        inputLayout.isEnabled = isEnabled

        edit.setText(currentValue)

        switch.setOnCheckedChangeListener { _, isChecked ->
            onSwitchChanged(isChecked)
            inputLayout.isEnabled = isChecked
        }

        edit.doAfterTextChanged { text ->
            if (text != null && text.isNotEmpty()) {
                onValueChanged(text.toString())
            }
        }
    }

    // Copy of your exact switch style helper
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
