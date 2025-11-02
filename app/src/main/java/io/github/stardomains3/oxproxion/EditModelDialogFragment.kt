package io.github.stardomains3.oxproxion


import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.core.graphics.toColorInt
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

class EditModelDialogFragment : DialogFragment() {

    var onModelAdded: ((LlmModel) -> Unit)? = null
    var onModelUpdated: ((LlmModel, LlmModel) -> Unit)? = null

    private var existingModel: LlmModel? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_add_model, null)

        val editName      = view.findViewById<EditText>(R.id.editModelName)
        val editApiId     = view.findViewById<EditText>(R.id.editApiIdentifier)
        val switchVision  = view.findViewById<MaterialSwitch>(R.id.switchVisionCapable)
        val switchReason  = view.findViewById<MaterialSwitch>(R.id.switchReasoningCapable)

        /* ----------  tint styling – unchanged  ---------- */
        val thumbTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)),
            intArrayOf("#000000".toColorInt(), "#686868".toColorInt()))
        val trackTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)),
            intArrayOf("#a0610a".toColorInt(), "#000000".toColorInt()))

        listOf(switchVision, switchReason).forEach {
            it.thumbTintList  = thumbTint
            it.trackTintList  = trackTint
            it.thumbTintMode  = PorterDuff.Mode.SRC_ATOP
            it.trackTintMode  = PorterDuff.Mode.SRC_ATOP
        }

        /* ----------  restore existing model  ---------- */
        existingModel = arguments?.let { args ->
            val dn = args.getString("displayName")
            val id = args.getString("apiIdentifier")
            if (!dn.isNullOrBlank() && !id.isNullOrBlank()) {
                LlmModel(
                    displayName  = dn,
                    apiIdentifier = id,
                    isVisionCapable = args.getBoolean("isVisionCapable", false),
                    isReasoningCapable = args.getBoolean("isReasoningCapable", false)
                )
            } else null
        }

        existingModel?.let { m ->
            editName.setText(m.displayName)
            editApiId.setText(m.apiIdentifier)
            switchVision.isChecked = m.isVisionCapable
            switchReason.isChecked = m.isReasoningCapable
            builder.setTitle("Edit Model")
            editApiId.setText(m.apiIdentifier)
        } ?: builder.setTitle("Add Model")

        /* ----------  watch the api-id field  ---------- */
        fun updateSwitchesVisibility() {
            val id = editApiId.text.toString().trim()
            val isSpecial = id.startsWith("@preset/", ignoreCase = true) ||
                    id.endsWith(":online", ignoreCase = true) ||
                    id.endsWith(":nitro",  ignoreCase = true) ||
                    id.endsWith(":floor",  ignoreCase = true)

            switchVision.visibility  = if (isSpecial) View.VISIBLE else View.GONE
            switchReason.visibility  = if (isSpecial) View.VISIBLE else View.GONE
        }
        editApiId.doAfterTextChanged { updateSwitchesVisibility() }
        updateSwitchesVisibility()   // initial call

        /* ----------  buttons  ---------- */
        builder.setView(view)
            .setPositiveButton("Save") { _, _ ->
                val name = editName.text.toString().trim()
                val id   = editApiId.text.toString().trim()

                if (name.isBlank() || id.isBlank()) return@setPositiveButton

                val isSpecial = id.startsWith("@preset/", ignoreCase = true) ||
                        id.endsWith(":online", ignoreCase = true) ||
                        id.endsWith(":nitro",  ignoreCase = true) ||
                        id.endsWith(":floor",  ignoreCase = true)

                val vision    = if (isSpecial) switchVision.isChecked  else false
                val reasoning = if (isSpecial) switchReason.isChecked else false



                val newModel = LlmModel(
                    displayName  = name,
                    apiIdentifier= id,
                    isVisionCapable     = vision,
                    isReasoningCapable  = reasoning
                )

                existingModel?.let { old -> onModelUpdated?.invoke(old, newModel) }
                    ?: onModelAdded?.invoke(newModel)
            }
            .setNegativeButton("Cancel", null)

        return builder.create()
    }
}