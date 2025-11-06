package io.github.stardomains3.oxproxion


import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.google.android.material.dialog.MaterialAlertDialogBuilder


class PresetChooserActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_autosend)  // Simple black layout

        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                showPresetChooser(sharedText)
            } else {
                finish()
            }
        } else {
            finish()
        }
    }

    private fun showPresetChooser(sharedText: String) {
        val presetRepository = PresetRepository(this)
        val allPresets = presetRepository.getAll()

        if (allPresets.isEmpty()) {
            // No presets available
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("No Presets Available")
                .setMessage("You don't have any presets yet. Create some in the app first.")
                .setPositiveButton("OK") { _, _ -> finish() }
                .setOnDismissListener { finish() }
                .show()
            return
        }

        val titles = allPresets.map { it.title }.toTypedArray()

        // Inflate custom dialog view (now with ScrollView)
        val dialogView = layoutInflater.inflate(R.layout.dialog_preset_chooser, null)
        val listView = dialogView.findViewById<ListView>(R.id.presetListView)
        val checkBox = dialogView.findViewById<CheckBox>(R.id.clearChatCheckBox)
        val prefsHelper = SharedPreferencesHelper(this)
        checkBox.isChecked = prefsHelper.getClearChatDefault()

        // NEW: Save the state whenever the user toggles the checkbox (sticks even on cancel)
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            prefsHelper.saveClearChatDefault(isChecked)
        }
        // Set up adapter (unchanged)
        val adapter = PresetDialogAdapter(this, titles)
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        if (titles.isNotEmpty()) {
            listView.setItemChecked(0, true)
        }

        // Set ListView to maxHeight (scrollable for long lists; no full measurement needed)
        val maxHeight = calculateMaxListHeight(this)
        val listParams = listView.layoutParams
        listParams.height = maxHeight.coerceAtLeast(200)  // Min 200px, even for short lists
        listView.layoutParams = listParams

        val titleTextView = TextView(this).apply {
            text = "Choose Preset"
            textSize = 20f
            setTextColor("#C2C2C2".toColorInt())
            gravity = Gravity.CENTER
            setPadding(24, 16, 24, 16)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setCustomTitle(titleTextView)
            .setView(dialogView)
            .setPositiveButton("Send") { dialogInterface, _ ->
                val selectedPosition = listView.checkedItemPosition
                if (selectedPosition != -1) {
                    val selectedPreset = allPresets[selectedPosition]
                    val clearChat = checkBox.isChecked
                    forwardToMainActivity(sharedText, clearChat, preset = selectedPreset, autosend = true)
                }
                dialogInterface.dismiss()
            }
            .setNeutralButton("Input Only") { dialogInterface, _ ->
                val selectedPosition = listView.checkedItemPosition
                if (selectedPosition != -1) {
                    val selectedPreset = allPresets[selectedPosition]
                    val clearChat = checkBox.isChecked
                    forwardToMainActivity(sharedText, clearChat, preset = selectedPreset, autosend = false, inputOnly = true)
                }
                dialogInterface.dismiss()
            }
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
                finish()
            }
            .setOnDismissListener {
                finish()
            }
            .show()

        // Ensure dialog window uses full available space
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }



    private fun forwardToMainActivity(sharedText: String, clearChat: Boolean, preset: Preset? = null, autosend: Boolean = false, inputOnly: Boolean = false) {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("shared_text", sharedText)
            putExtra("clear_chat", clearChat)
            if (preset != null) {
                putExtra("apply_preset", true)  // UNIQUE FLAG for preset handling
                putExtra("preset_id", preset.id)
                putExtra("preset_title", preset.title)
                putExtra("preset_model", preset.modelIdentifier)
                putExtra("preset_system_message_title", preset.systemMessage.title)
                putExtra("preset_system_message_prompt", preset.systemMessage.prompt)
                putExtra("preset_system_message_is_default", preset.systemMessage.isDefault)
                putExtra("preset_streaming", preset.streaming)
                putExtra("preset_reasoning", preset.reasoning)
                putExtra("preset_conversation_mode", preset.conversationMode)
            }
            // Use NEW names to avoid conflicts
            if (autosend) putExtra("autosend_preset", true)
            if (inputOnly) putExtra("input_only_preset", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(mainIntent)
        finish()
    }

    private fun calculateMaxListHeight(context: Context): Int {
        val wm = context.getSystemService(WindowManager::class.java)
        val screenHeight = wm.maximumWindowMetrics.bounds.height()  // FIXED: Added get()
        // Reduced subtraction: Just status bar (~48dp); modern devices often have no nav bar
        val density = context.resources.displayMetrics.density
        val estimatedStatusBar = 48 * density
        val usableHeight = (screenHeight - estimatedStatusBar.toInt()).coerceAtLeast(0)
        return (usableHeight * 0.8f).toInt()  // 80% for generous space; tweak to 0.7f if dialog feels too tall
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
