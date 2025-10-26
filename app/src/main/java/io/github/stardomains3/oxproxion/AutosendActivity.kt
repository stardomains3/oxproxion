package io.github.stardomains3.oxproxion

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AutosendActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_autosend)  // Simple black layout (see below)

        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    putExtra("autosend", true)  // Flag to trigger autosend
                    putExtra("shared_text", sharedText)  // The shared text
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(mainIntent)
            }
        }
        finish()  // Close this activity immediately after forwarding
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        finish()  // Ensure it closes quickly
    }
}
