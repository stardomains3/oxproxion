package io.github.stardomains3.oxproxion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

class MainActivity : AppCompatActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    private lateinit var biometricExecutor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* ------------------------------------------------------ */
        /* 1.  Cold-start gate:  finish() if auth fails / none    */
        /* ------------------------------------------------------ */

        if (savedInstanceState == null && SharedPreferencesHelper(this).getBiometricEnabled()) {
            val bm = BiometricManager.from(this)
            when (bm.canAuthenticate(BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS -> showBiometricGate()
                else -> {
                    // Optional: Auto-disable to avoid repeated warnings on future launches
                    SharedPreferencesHelper(this).saveBiometricEnabled(false)

                    Toast.makeText(
                        this,
                        "Biometrics unavailable—proceeding without lock. Enable in Settings > Security for added security.",
                        Toast.LENGTH_LONG
                    ).show()

                    // Log for debugging/analytics if needed
                    //Log.w("MainActivity", "Biometrics skipped: ${bm.canAuthenticate(BIOMETRIC_STRONG)}")

                    continueOnCreate() // Proceed without auth
                    return
                }
            }
            return // Still halt until auth succeeds if available
        }

        /* ------------------------------------------------------ */
        /* 2.  Normal onCreate continues only after unlock        */
        /* ------------------------------------------------------ */
        continueOnCreate()
    }

    private fun showBiometricGate() {
        biometricExecutor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, biometricExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(this@MainActivity, "Authentication error", Toast.LENGTH_SHORT).show()
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(this@MainActivity, "Authentication failed", Toast.LENGTH_SHORT).show()
                    finish()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    continueOnCreate() // unlock OK → proceed
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock oxproxion")
            .setSubtitle("Use your biometric credential")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun continueOnCreate() {
        setContentView(R.layout.activity_main)
        askNotificationPermission()
        val sharedPreferencesHelper = SharedPreferencesHelper(this)
        sharedPreferencesHelper.seedDefaultModelsIfNeeded()
        sharedPreferencesHelper.seedDefaultSystemMessagesIfNeeded()

        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            val chatFragment = ChatFragment().apply {
                arguments = Bundle().apply {
                    if (intent?.action == Intent.ACTION_SEND && "text/plain" == intent.type) {
                        putString("shared_text", intent.getStringExtra(Intent.EXTRA_TEXT))
                    }
                    if (intent?.action in listOf(Intent.ACTION_ASSIST, Intent.ACTION_VOICE_COMMAND)) {
                        putBoolean("start_stt_on_launch", true)
                    }
                }
            }
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, chatFragment)
                .commitNow()
        }

        val vm: ChatViewModel by viewModels()
        if (intent.getBooleanExtra("autosend", false)) {
            intent.getStringExtra("shared_text")?.let { text ->
                val clearChat = intent.getBooleanExtra("clear_chat", false)
                if (clearChat) vm.startNewChat()
                vm.consumeSharedTextautosend(text)
            }
        } else if (intent.getBooleanExtra("input_only", false)) {
            intent.getStringExtra("shared_text")?.let { text ->
                val clearChat = intent.getBooleanExtra("clear_chat", false)
                if (clearChat) vm.startNewChat()
                vm.consumeSharedText(text)
                val sharedPreferencesHelper = SharedPreferencesHelper(this)
                val systemMessageTitle = sharedPreferencesHelper.getSelectedSystemMessage().title
                Toast.makeText(this, systemMessageTitle, Toast.LENGTH_SHORT).show()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                val handled = (fragment as? ChatFragment)?.onBackPressed() ?: false
                if (!handled) {
                    if (supportFragmentManager.backStackEntryCount == 0) moveTaskToBack(true)
                    else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        if (sharedPreferencesHelper.getNotiPreference()) startForegroundService()
    }


    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startForegroundService() {
        if (ForegroundService.isRunningForeground) return
        try {
            val serviceIntent = Intent(this, ForegroundService::class.java)
            val vm: ChatViewModel by viewModels()
            val displayName = vm.getModelDisplayName(vm.activeChatModel.value ?: "Unknown Model")
            serviceIntent.putExtra("initial_title", displayName)
            startService(serviceIntent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start foreground service", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (intent.action == Intent.ACTION_SEND && "text/plain" == intent.type && !intent.getBooleanExtra("autosend", false)) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                val vm: ChatViewModel by viewModels()
                val sharedPreferencesHelper = SharedPreferencesHelper(this)
                vm.consumeSharedText(text)
                val systemMessageTitle = sharedPreferencesHelper.getSelectedSystemMessage().title
                Toast.makeText(this, systemMessageTitle, Toast.LENGTH_SHORT).show()
            }
        }

        val vm: ChatViewModel by viewModels()
        if (intent.getBooleanExtra("autosend", false)) {
            intent.getStringExtra("shared_text")?.let { text ->
                val clearChat = intent.getBooleanExtra("clear_chat", false)
                if (clearChat) vm.startNewChat()
                vm.consumeSharedTextautosend(text)
            }
        } else if (intent.getBooleanExtra("input_only", false)) {
            intent.getStringExtra("shared_text")?.let { text ->
                val clearChat = intent.getBooleanExtra("clear_chat", false)
                if (clearChat) vm.startNewChat()
                vm.consumeSharedText(text)
                val sharedPreferencesHelper = SharedPreferencesHelper(this)
                val systemMessageTitle = sharedPreferencesHelper.getSelectedSystemMessage().title
                Toast.makeText(this, systemMessageTitle, Toast.LENGTH_SHORT).show()
            }
        }

        val isAssistLaunch = intent.action in listOf(Intent.ACTION_ASSIST)
        if (isAssistLaunch) {
            val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? ChatFragment
            fragment?.startSpeechRecognitionSafely()
        }
    }
}
