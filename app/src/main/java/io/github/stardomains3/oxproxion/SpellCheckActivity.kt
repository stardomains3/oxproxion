package io.github.stardomains3.oxproxion

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt

class SpellCheckActivity : AppCompatActivity() {

    private lateinit var vm: ChatViewModel
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var resultScrollView: ScrollView
    private lateinit var resultText: TextView
    private lateinit var buttonContainer: LinearLayout
    private lateinit var cancelButton: AppCompatButton
    private lateinit var acceptButton: AppCompatButton
    private lateinit var loadingCancelButton: ImageView

    private var correctedResult: String? = null
    private var activeJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Setup the "Floating Card" UI
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 48, 64, 48)
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
            setTextColor("#C2C2C2".toColorInt())
            setPadding(0, 0, 0, 24)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        layout.addView(titleView)

        // Progress Bar with Cancel Button container
        val loadingContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Progress Bar (Material Style)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf("#C2C2C2".toColorInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginEnd = 120 // Make room for cancel button
            }
        }
        loadingContainer.addView(progressBar)

        // Circular Cancel Button using ic_cancel.xml (shown during loading)
        loadingCancelButton = ImageView(this).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_cancel))
            scaleType = ImageView.ScaleType.FIT_CENTER  // Scales icon to fit
            setPadding(8, 8, 8, 8)  // Less padding so icon is bigger
            layoutParams = FrameLayout.LayoutParams(96, 96).apply {  // Bigger: 96x96
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
            }
            setOnClickListener { onLoadingCancel() }
        }
        loadingContainer.addView(loadingCancelButton)

        layout.addView(loadingContainer)

        // Status Text
        statusText = TextView(this).apply {
            text = "Consulting AI..."
            textSize = 14f
            setTextColor("#C2C2C2".toColorInt())
            setPadding(0, 16, 0, 0)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        layout.addView(statusText)

        // Result ScrollView (initially hidden)
        resultScrollView = ScrollView(this).apply {
            visibility = View.GONE
            setPadding(0, 16, 0, 16)
        }.also { scroll ->
            resultText = TextView(this).apply {
                textSize = 16f
                setTextColor("#FFFFFF".toColorInt())
                setLineSpacing(0f, 1.3f)
            }
            scroll.addView(resultText)
        }
        layout.addView(resultScrollView)

        // Button Container (initially hidden)
        buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            setPadding(0, 16, 0, 0)
        }

        // Cancel Button
        cancelButton = AppCompatButton(this).apply {
            text = "Cancel"
            textSize = 14f
            setTextColor("#C2C2C2".toColorInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f
                setColor("#2A2A2A".toColorInt())
            }
            setPadding(48, 24, 48, 24)
            setOnClickListener { onCancel() }
        }
        buttonContainer.addView(cancelButton)

        // Spacer between buttons
        buttonContainer.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 0)
        })

        // Accept Button
        acceptButton = AppCompatButton(this).apply {
            text = "Accept"
            textSize = 14f
            setTextColor("#471d0d".toColorInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f
                setColor("#C2C2C2".toColorInt())
            }
            setPadding(48, 24, 48, 24)
            setOnClickListener { onAccept() }
        }
        buttonContainer.addView(acceptButton)

        layout.addView(buttonContainer)

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
            Toast.makeText(this, "Text is read-only. AI cannot replace it here.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        processText(selectedText)
    }

    private fun processText(inputText: String) {
        activeJob = lifecycleScope.launch {
            try {
                statusText.text = "Sending to AI..."
                val rawText = vm.getAIFixContent(inputText)

                if (!rawText.isNullOrBlank()) {
                    // Check if the first line starts with the tag
                    val cleanedText = if (rawText.trimStart().startsWith("</think>")) {
                        // This grabs everything after the first newline character
                        rawText.substringAfter('\n').trim()
                    } else {
                        rawText
                    }

                    correctedResult = cleanedText
                    showResult(cleanedText)
                } else {
                    showError("AI couldn't find any fixes for this text.")
                }
            } catch (e: Exception) {
                // Don't show error if user cancelled
                if (e is kotlinx.coroutines.CancellationException) {
                   // Log.d("AIFix", "User cancelled the request")
                    finish()
                    return@launch
                }

              //  Log.e("AIFix", "Correction failed", e)
                val errorMessage = when {
                    e.message?.contains("timeout", ignoreCase = true) == true ->
                        "Request timed out. Please try again."
                    e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                            e.message?.contains("ConnectException", ignoreCase = true) == true ->
                        "Error connecting with AI. Check your internet connection."
                    e.message?.contains("API Error", ignoreCase = true) == true ->
                        "Error connecting with AI. The service may be unavailable."
                    e.message?.contains("401", ignoreCase = true) == true ||
                            e.message?.contains("Unauthorized", ignoreCase = true) == true ->
                        "Authentication error. Check your API key."
                    else -> "Error connecting with AI. Please try again."
                }
                showError(errorMessage)
            }
        }
    }

    private fun onLoadingCancel() {
        activeJob?.cancel()
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun showResult(correctedText: String) {
        loadingCancelButton.visibility = View.GONE
        progressBar.visibility = View.GONE
        statusText.text = "AI Suggestion:"

        resultText.text = correctedText
        resultScrollView.visibility = View.VISIBLE
        buttonContainer.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        loadingCancelButton.visibility = View.GONE
        progressBar.visibility = View.GONE
        statusText.apply {
            text = message
            setTextColor("#FF6B6B".toColorInt()) // Red color for errors
        }

        // Show just a "Close" button (relabel Cancel)
        cancelButton.text = "Close"
        buttonContainer.visibility = View.VISIBLE
    }

    private fun onCancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun onAccept() {
        correctedResult?.let { result ->
            val resultIntent = Intent().apply {
                putExtra(Intent.EXTRA_PROCESS_TEXT, result)
            }
            setResult(RESULT_OK, resultIntent)
        }
        finish()
    }
}
