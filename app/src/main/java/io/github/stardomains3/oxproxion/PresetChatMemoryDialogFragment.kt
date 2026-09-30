package io.github.stardomains3.oxproxion

import android.app.Dialog
import android.os.Bundle
import android.widget.CheckedTextView
import androidx.core.graphics.toColorInt
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PresetChatMemoryDialogFragment : DialogFragment() {

    /** Called with the chosen count (Int.MAX_VALUE == "All messages"). */
    var onSelected: ((Int) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentCount = arguments?.getInt(ARG_CURRENT, Int.MAX_VALUE) ?: Int.MAX_VALUE

        val options = arrayOf(
            "2 messages", "4 messages", "6 messages", "8 messages",
            "10 messages", "12 messages", "16 messages", "20 messages", "All messages"
        )

        val checkedItem = when (currentCount) {
            Int.MAX_VALUE -> 8
            else -> {
                val idx = options.indexOfFirst {
                    it.startsWith(currentCount.toString()) &&
                            (it.length == currentCount.toString().length ||
                                    it[currentCount.toString().length] == ' ')
                }
                if (idx >= 0) idx else 3
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CustomMaterialAlertDialogTheme)
            .setTitle("Chat Memory")
            .setSingleChoiceItems(options, checkedItem) { d, which ->
                val selectedText = options[which]
                val count = if (selectedText == "All messages") Int.MAX_VALUE
                else selectedText.split(" ")[0].toInt()
                onSelected?.invoke(count)
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val listView = dialog.listView
            if (listView != null) {
                for (i in 0 until listView.childCount) {
                    (listView.getChildAt(i) as? CheckedTextView)
                        ?.setTextColor("#C2C2C2".toColorInt())
                }
            }
        }
        return dialog
    }

    companion object {
        private const val ARG_CURRENT = "current"
        fun newInstance(current: Int) = PresetChatMemoryDialogFragment().apply {
            arguments = Bundle().apply { putInt(ARG_CURRENT, current) }
        }
    }
}
