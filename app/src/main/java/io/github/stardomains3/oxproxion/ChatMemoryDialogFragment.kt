package io.github.stardomains3.oxproxion

import android.app.Dialog
import android.os.Bundle
import android.widget.CheckedTextView
import androidx.core.graphics.toColorInt
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ChatMemoryDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val prefs = SharedPreferencesHelper(requireContext())
        val currentCount = prefs.getChatMemoryCount()

        val options = arrayOf(
            "2 messages", "4 messages", "6 messages", "8 messages",
            "10 messages", "12 messages", "16 messages", "20 messages", "All messages"
        )

        val checkedItem = when (currentCount) {
            Int.MAX_VALUE -> 8
            else -> {
                val index = options.indexOfFirst {
                    it.startsWith(currentCount.toString()) &&
                            (it.length == currentCount.toString().length || it[currentCount.toString().length] == ' ')
                }
                if (index >= 0) index else 3
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.CustomMaterialAlertDialogTheme)
            .setTitle("Chat Memory")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val selectedText = options[which]
                val count = if (selectedText == "All messages") {
                    Int.MAX_VALUE
                } else {
                    selectedText.split(" ")[0].toInt()
                }

                prefs.saveChatMemoryCount(count)

                val button = requireActivity().findViewById<MaterialButton>(R.id.chatMemoryButton)
                button?.text = if (count == Int.MAX_VALUE) "All messages" else "$count messages"

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .create()

        // Override text color after dialog is shown
        dialog.setOnShowListener {
            val listView = dialog.listView
            if (listView != null) {
                for (i in 0 until listView.childCount) {
                    val child = listView.getChildAt(i)
                    if (child is CheckedTextView) {
                        child.setTextColor("#C2C2C2".toColorInt())
                    }
                }
            }
        }

        return dialog
    }
}

