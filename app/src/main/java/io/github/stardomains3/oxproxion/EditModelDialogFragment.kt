package io.github.stardomains3.oxproxion

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.os.Bundle
import android.widget.EditText
import androidx.core.graphics.toColorInt
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

class EditModelDialogFragment : DialogFragment() {

    var onModelAdded: ((LlmModel) -> Unit)? = null
    var onModelUpdated: ((LlmModel, LlmModel) -> Unit)? = null

    private var existingModel: LlmModel? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireActivity())
        val view = layoutInflater.inflate(R.layout.dialog_add_model, null)

        val editName            = view.findViewById<EditText>(R.id.editModelName)
        val editApiId           = view.findViewById<EditText>(R.id.editApiIdentifier)
        val switchVision        = view.findViewById<MaterialSwitch>(R.id.switchVisionCapable)
        val switchReason        = view.findViewById<MaterialSwitch>(R.id.switchReasoningCapable)
        val switchLan           = view.findViewById<MaterialSwitch>(R.id.switchLanModel)
        val switchImage         = view.findViewById<MaterialSwitch>(R.id.switchImageGen)
        val switchTranscription = view.findViewById<MaterialSwitch>(R.id.switchTranscription)
        val switchIsFree        = view.findViewById<MaterialSwitch>(R.id.switchIsFree)

        /* ----------  tint styling  ---------- */
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

        listOf(switchVision, switchReason, switchLan, switchImage, switchTranscription, switchIsFree).forEach {
            it.thumbTintList  = thumbTint
            it.trackTintList  = trackTint
            it.thumbTintMode  = PorterDuff.Mode.SRC_ATOP
            it.trackTintMode  = PorterDuff.Mode.SRC_ATOP
        }

        /* ----------  restore existing model  ---------- */
        existingModel = arguments?.let { args ->
            val dn = args.getString("displayName")
            val id = args.getString("apiIdentifier")
            val created = args.getLong("created", 0L)
            val isFree = args.getBoolean("isFree", false)
            val isImageGen = args.getBoolean("isImageGenerationCapable", false)
            val isTranscription = args.getBoolean("isTranscription", false)
            if (!dn.isNullOrBlank() && !id.isNullOrBlank()) {
                LlmModel(
                    displayName              = dn,
                    apiIdentifier            = id,
                    isVisionCapable          = args.getBoolean("isVisionCapable", false),
                    isReasoningCapable       = args.getBoolean("isReasoningCapable", false),
                    isImageGenerationCapable = isImageGen,
                    isTranscription          = isTranscription,
                    created                  = created,
                    isLANModel               = args.getBoolean("isLANModel", false),
                    isFree                   = isFree
                )
            } else null
        }

        existingModel?.let { m ->
            editName.setText(m.displayName)
            editApiId.setText(m.apiIdentifier)
            switchVision.isChecked        = m.isVisionCapable
            switchReason.isChecked        = m.isReasoningCapable
            switchLan.isChecked           = m.isLANModel
            switchImage.isChecked         = m.isImageGenerationCapable
            switchTranscription.isChecked = m.isTranscription
            switchIsFree.isChecked        = m.isFree
            builder.setTitle("Edit Model")
        } ?: builder.setTitle("Add Model")

        /* ----------  buttons  ---------- */
        builder.setView(view)
            .setPositiveButton("Save") { _, _ ->
                val name = editName.text.toString().trim()
                val id   = editApiId.text.toString().trim()

                if (name.isBlank() || id.isBlank()) return@setPositiveButton

                val vision          = switchVision.isChecked
                val reasoning       = switchReason.isChecked
                val isLan           = switchLan.isChecked
                val isImage         = switchImage.isChecked
                val isTranscription = switchTranscription.isChecked
                val isFree          = switchIsFree.isChecked

                val createdTimestamp = existingModel?.created ?: (System.currentTimeMillis() / 1000)

                val newModel = LlmModel(
                    displayName              = name,
                    apiIdentifier            = id,
                    isVisionCapable          = vision,
                    isImageGenerationCapable = isImage,
                    isReasoningCapable       = reasoning,
                    isTranscription          = isTranscription,
                    created                  = createdTimestamp,
                    isLANModel               = isLan,
                    isFree                   = isFree
                )

                existingModel?.let { old -> onModelUpdated?.invoke(old, newModel) }
                    ?: onModelAdded?.invoke(newModel)
            }
            .setNegativeButton("Cancel", null)

        return builder.create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            val metrics = resources.displayMetrics
            val maxHeight = (metrics.heightPixels * 0.85).toInt()
            val maxWidth = (metrics.widthPixels * 0.90).toInt()

            window.setLayout(maxWidth, maxHeight)
        }
    }
}
