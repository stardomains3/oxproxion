package io.github.stardomains3.oxproxion

import android.app.AlertDialog
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Switch
import androidx.core.graphics.toColorInt
import androidx.fragment.app.DialogFragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.switchmaterial.SwitchMaterial

class EditModelDialogFragment : DialogFragment() {

    var onModelAdded: ((LlmModel) -> Unit)? = null
    var onModelUpdated: ((LlmModel, LlmModel) -> Unit)? = null

    private var existingModel: LlmModel? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.dialog_add_model, null)

        val editName = view.findViewById<EditText>(R.id.editModelName)
        val editApiId = view.findViewById<EditText>(R.id.editApiIdentifier)
        val switchVision = view.findViewById< MaterialSwitch>(R.id.switchVisionCapable)
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
        switchVision.trackTintList = trackTintSelector
        switchVision.thumbTintList = thumbTintSelector
        switchVision.thumbTintMode = PorterDuff.Mode.SRC_ATOP
        switchVision.trackTintMode = PorterDuff.Mode.SRC_ATOP

        existingModel = arguments?.let { args ->
            val displayName = args.getString("displayName")
            val apiIdentifier = args.getString("apiIdentifier")
            if (displayName != null && apiIdentifier != null) {
                LlmModel(displayName, apiIdentifier, args.getBoolean("isVisionCapable", false))
            } else {
                null
            }
        }

        existingModel?.let { model ->
            editName.setText(model.displayName)
            editApiId.setText(model.apiIdentifier)
            switchVision.isChecked = model.isVisionCapable
            builder.setTitle("Edit Model")
        } ?: run {
            builder.setTitle("Add Model")
        }

        builder.setView(view)
            .setPositiveButton("Save") { _, _ ->
                val name = editName.text.toString().trim()
                val apiId = editApiId.text.toString().trim()
                val visionCapable = switchVision.isChecked

                if (name.isNotBlank() && apiId.isNotBlank()) {
                    val newModel = LlmModel(name, apiId, visionCapable)

                    existingModel?.let {
                        onModelUpdated?.invoke(it, newModel)
                    } ?: run {
                        onModelAdded?.invoke(newModel)
                    }
                }
            }
            .setNegativeButton("Cancel", null)

        return builder.create()
    }

}