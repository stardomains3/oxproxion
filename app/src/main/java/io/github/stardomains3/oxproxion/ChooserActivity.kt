package io.github.stardomains3.oxproxion

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ChooserActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_autosend)  // Simple black layout

        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                showSystemMessageChooser(sharedText)
            } else {
                finish()
            }
        } else {
            finish()
        }
    }

    private fun showSystemMessageChooser(sharedText: String) {
        val sharedPreferencesHelper = SharedPreferencesHelper(this)
        val allMessages = sharedPreferencesHelper.getCustomSystemMessages() + sharedPreferencesHelper.getDefaultSystemMessage()
        val titles = allMessages.map { it.title }.toTypedArray()
        val currentMessage = sharedPreferencesHelper.getSelectedSystemMessage()
        val currentIndex = allMessages.indexOfFirst { it.title == currentMessage.title && it.prompt == currentMessage.prompt }

        // NEW: Get the active model's display name
        val vm = ViewModelProvider(this).get(ChatViewModel::class.java)
        val modelDisplayName = vm.getModelDisplayName(vm.activeChatModel.value ?: "Unknown Model")

        // Inflate custom dialog view
        val dialogView = layoutInflater.inflate(R.layout.dialog_chooser, null)
        val listView = dialogView.findViewById<ListView>(R.id.systemMessageListView)
        val checkBox = dialogView.findViewById<CheckBox>(R.id.clearChatCheckBox)

        // Set up adapter with highlighting
        val adapter = SystemMessageDialogAdapterCh(this, titles, currentIndex)
        listView.adapter = adapter
        listView.setItemChecked(currentIndex, true)  // Pre-select current
        val titleTextView = TextView(this).apply {
            text = "Choose and Send\nModel: $modelDisplayName"
            textSize = 20f  // Adjust size as needed
            setTextColor(Color.parseColor("#C2C2C2"))
            gravity = Gravity.CENTER  // Center vertically and horizontally
            setPadding(24, 16, 24, 16)  // Add padding for better spacing
        }
        MaterialAlertDialogBuilder(this)
            .setCustomTitle(titleTextView)
            .setView(dialogView)
            .setPositiveButton("Send") { dialog, _ ->
                val selectedPosition = listView.checkedItemPosition
                if (selectedPosition != -1) {
                    val selectedMessage = allMessages[selectedPosition]
                    sharedPreferencesHelper.saveSelectedSystemMessage(selectedMessage)
                    val clearChat = checkBox.isChecked
                    forwardToMainActivity(sharedText, clearChat, autosend = true)
                }
                dialog.dismiss()
            }
            .setNeutralButton("Input Only") { dialog, _ ->
                val selectedPosition = listView.checkedItemPosition
                if (selectedPosition != -1) {
                    val selectedMessage = allMessages[selectedPosition]
                    sharedPreferencesHelper.saveSelectedSystemMessage(selectedMessage)
                    val clearChat = checkBox.isChecked
                    forwardToMainActivity(sharedText, clearChat, autosend = false, inputOnly = true)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setOnDismissListener {
                finish()
            }
            .show()
    }


    private fun forwardToMainActivity(sharedText: String, clearChat: Boolean, autosend: Boolean = false, inputOnly: Boolean = false) {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("shared_text", sharedText)
            putExtra("clear_chat", clearChat)
            if (autosend) putExtra("autosend", true)
            if (inputOnly) putExtra("input_only", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(mainIntent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        finish()
    }
}
