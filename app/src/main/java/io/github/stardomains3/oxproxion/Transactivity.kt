package io.github.stardomains3.oxproxion

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText

import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.media.ToneGenerator
import android.media.AudioManager
import android.view.KeyEvent
import android.view.View
import androidx.core.view.isVisible


class Transactivity : AppCompatActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private var recordStartMs = 0L
    private var mediaRecorder: MediaRecorder? = null
    private var voiceRecordFile: File? = null
    private var isRecording = false
    private lateinit var btnQuit: Button
    private lateinit var btnCopy: Button

    // UI Elements
    private lateinit var etTranscription: EditText
    private lateinit var btnAction: Button

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceRecording()
        } else {
            etTranscription.setText("Permission denied. Cannot transcribe.")
            btnAction.text = "Retry"
            btnAction.isVisible = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transcription)

        // Initialize UI elements
        etTranscription = findViewById(R.id.et_transcription)
        btnAction = findViewById(R.id.btn_action)
        btnQuit = findViewById(R.id.btn_quit)
        btnCopy = findViewById(R.id.btn_copy)

        btnAction.visibility = View.GONE
        btnQuit.visibility = View.GONE
        btnCopy.visibility = View.GONE
        etTranscription.setText("")



        btnQuit.setOnClickListener {
            if (isRecording) {
                try {
                    mediaRecorder?.stop()
                } catch (_: Exception) {}
                try {
                    mediaRecorder?.release()
                } catch (_: Exception) {}
                mediaRecorder = null
                isRecording = false
            }
            finish()
        }

        btnCopy.setOnClickListener {
            copyToClipboard(etTranscription.text.toString())
            finish()
        }

        btnAction.setOnClickListener {
            if (isRecording) {
                stopVoiceRecording()
            } else {
                startVoiceRecording()
            }
        }

        // START AUTOMATICALLY
        window.decorView.postDelayed({
            startVoiceRecording()
        }, 200)
    }

    // Intercept volume keys to stop recording
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isRecording && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            stopVoiceRecording()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun startVoiceRecording() {
        btnCopy.visibility = View.GONE
        btnQuit.visibility = View.GONE
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 140)
        try {
            voiceRecordFile = File(cacheDir, "transcription_${System.currentTimeMillis()}.opus")

            mediaRecorder = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.OGG)
                setOutputFile(voiceRecordFile!!.absolutePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(32000)
                prepare()
                start()
            }

            recordStartMs = System.currentTimeMillis()

            isRecording = true
            etTranscription.setText("Listening...")
            btnAction.visibility = View.VISIBLE
            btnAction.text = "Stop / Transcribe"
            btnQuit.visibility = View.VISIBLE

        } catch (e: Exception) {
            Log.e("Transactivity", "Failed to start recording", e)
            etTranscription.setText("Error starting mic")
            btnAction.text = "Retry"
            btnAction.isVisible = true
        }
    }

    private fun stopVoiceRecording() {
        val duration = System.currentTimeMillis() - recordStartMs
        val recorder = mediaRecorder

        mediaRecorder = null
        isRecording = false
        btnAction.visibility = View.GONE

        var stopSuccessful = true

        try {
            if (duration >= 400) {
                recorder?.stop()
            } else {
                stopSuccessful = false
            }
        } catch (e: Exception) {
            Log.e("Transactivity", "Failed to stop MediaRecorder", e)
            stopSuccessful = false
        } finally {
            try {
                recorder?.release()
            } catch (e: Exception) {
                Log.e("Transactivity", "Failed to release MediaRecorder", e)
            }
        }

        val file = voiceRecordFile
        voiceRecordFile = null

        if (stopSuccessful && duration >= 400 && file != null && file.exists() && file.length() > 1024) {
            processVoiceRecording(file)
        } else {
            file?.delete()
            if (duration < 400) {
                etTranscription.setText("Recording was too short")
            } else {
                etTranscription.setText("Error: Empty or corrupted recording")
            }
            btnAction.text = "Try Again"
            btnAction.visibility = View.VISIBLE
        }
    }

    private fun processVoiceRecording(file: File) {
        lifecycleScope.launch {
            try {
                etTranscription.setText("Transcribing...")

                val audioBytes = withContext(Dispatchers.IO) {
                    file.readBytes()
                }

                val transcribedText = viewModel.transcribeAudioForInput(
                    audioBytes = audioBytes,
                    audioFormat = "opus",
                    fileName = file.name
                )

                if (!transcribedText.isNullOrBlank()) {
                    // 1. Show the result — editable so user can fix words
                    etTranscription.setText(transcribedText)
                    etTranscription.requestFocus()

                    // 2. Show Copy, Try Again, and Quit buttons
                    btnCopy.visibility = View.VISIBLE
                    btnAction.text = "Try Again"
                    btnAction.visibility = View.VISIBLE
                    btnQuit.visibility = View.VISIBLE

                    // No automatic copy, no auto-close
                } else {
                    etTranscription.setText("Could not understand audio.")
                    btnAction.text = "Try Again"
                    btnAction.isVisible = true
                    btnAction.isEnabled = true
                    btnQuit.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e("Transactivity", "Transcription error", e)
                etTranscription.setText("Error: ${e.message}")
                btnAction.text = "Try Again"
                btnAction.isVisible = true
                btnAction.isEnabled = true
                btnQuit.visibility = View.VISIBLE
            } finally {
                file.delete()
                voiceRecordFile = null
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Transcribed Text", text)
        clipboard.setPrimaryClip(clip)
    }

    override fun onPause() {
        super.onPause()
        if (isRecording) {
            try {
                mediaRecorder?.stop()
            } catch (_: Exception) {}
            try {
                mediaRecorder?.release()
            } catch (_: Exception) {}
            mediaRecorder = null
            isRecording = false
        }
    }
}
