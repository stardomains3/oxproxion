package io.github.stardomains3.oxproxion

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton

class ChatMemoryDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val prefs = SharedPreferencesHelper(requireContext())
        val currentCount = prefs.getChatMemoryCount()

        val options = arrayOf("2 messages", "4 messages", "6 messages", "8 messages", "10 messages", "12 messages", "16 messages", "20 messages", "All messages")
        val checkedItem = when (currentCount) {
            Int.MAX_VALUE -> 8 // "All" is at index 8
            else -> {
                val index = options.indexOfFirst { it.startsWith(currentCount.toString()) }
                if (index >= 0) index else 3 // Default to "8" (index 3) if not found
            }
        }

        return AlertDialog.Builder(requireContext())
            .setTitle("Chat Memory")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val count = when {
                    options[which].startsWith("All") -> Int.MAX_VALUE
                    else -> options[which].split(" ")[0].toInt()
                }
                prefs.saveChatMemoryCount(count)

                // Update the button text in SettingsFragment
                val button = requireActivity().findViewById<MaterialButton>(R.id.chatMemoryButton)
                button?.text = if (count == Int.MAX_VALUE) "All messages" else "$count messages"

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .create()
    }
}