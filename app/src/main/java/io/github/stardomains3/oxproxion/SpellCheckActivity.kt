package io.github.stardomains3.oxproxion

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt

class SpellCheckActivity : AppCompatActivity() {

    private lateinit var vm: ChatViewModel
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Setup the "Floating Card" UI
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
            gravity = Gravity.CENTER_HORIZONTAL

            // Background: Rounded corners + Dark Brown (#471d0d) from your theme
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 48f
                setColor("#471d0d".toColorInt())
            }
        }

        // Title
        val titleView = TextView(this).apply {
            text = "AI Grammar Fix"
            textSize = 18f
            setTextColor("#C2C2C2".toColorInt()) // Your theme light gray
            setPadding(0, 0, 0, 32)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        layout.addView(titleView)

        // Progress Bar (Material Style)
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            // Setting the tint to your accent/orange if available, else light gray
            indeterminateTintList = android.content.res.ColorStateList.valueOf("#C2C2C2".toColorInt())
        }
        layout.addView(progressBar)

        // Status Text
        statusText = TextView(this).apply {
            text = "Consulting AI..."
            textSize = 14f
            setTextColor("#C2C2C2".toColorInt())
            setPadding(0, 24, 0, 0)
        }
        layout.addView(statusText)

        setContentView(layout)

        // 2. Adjust Window to be 85% width and centered
        val params = window.attributes
        val displayMetrics = resources.displayMetrics
        params.width = (displayMetrics.widthPixels * 0.85).toInt()
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        params.gravity = Gravity.CENTER
        window.attributes = params

        // 3. Initialize Logic
        vm = ViewModelProvider(this)[ChatViewModel::class.java]
        handleIntent()
    }

    private fun handleIntent() {
        val selectedText = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        val isReadOnly = intent?.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false) ?: false

        if (selectedText.isNullOrBlank()) {
            finish()
            return
        }

        if (isReadOnly) {
            // User selected text in a place like Chrome where we can't write back
            Toast.makeText(this, "Text is read-only. AI cannot replace it here.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        processText(selectedText)
    }

    private fun processText(inputText: String) {
        lifecycleScope.launch {
            try {
                // The heavy lifting
                val correctedText = vm.getAIFixContent(inputText)

                if (!correctedText.isNullOrBlank()) {
                    val resultIntent = Intent().apply {
                        putExtra(Intent.EXTRA_PROCESS_TEXT, correctedText)
                    }
                    setResult(RESULT_OK, resultIntent)
                    statusText.text = "Success!"
                } else {
                    setResult(RESULT_CANCELED)
                    Toast.makeText(this@SpellCheckActivity, "AI couldn't find a fix.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                setResult(RESULT_CANCELED)
            } finally {
                // Brief delay so the user sees the "Success" state before it vanishes
                delay(400)
                finish()
            }
        }
    }
}
