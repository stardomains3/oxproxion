package io.github.stardomains3.oxproxion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { SharedPreferencesHelper(this) }
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }



    private var awaitingBiometricAuth = false
    override fun onStart() {
        super.onStart()
        if (!awaitingBiometricAuth) {
            ensureServiceRunning()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {

        /* ------------------------------------------------------ */
        /* 0. Theme setup — MUST run before super.onCreate()      */
        /* ------------------------------------------------------ */


        // Night mode (move it here from ChatFragment's global-layout hack)
        when (prefs.getThemeMode()) {
            SharedPreferencesHelper.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            SharedPreferencesHelper.THEME_DARK  -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }.let { AppCompatDelegate.setDefaultNightMode(it) }

        // AMOLED overlay
        val isDark = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (isDark && prefs.isAmoledModeEnabled()) {
            theme.applyStyle(R.style.ThemeOverlay_Oxproxion_Amoled, true)
        }

        super.onCreate(savedInstanceState)
// Full-document render for WebView-based PDF printing (printButton)
        android.webkit.WebView.enableSlowWholeDocumentDraw()
        /* ------------------------------------------------------ */
        /* 1.  Cold-start gate:  finish() if auth fails / none    */
        /* ------------------------------------------------------ */


        if (savedInstanceState == null && prefs.getBiometricEnabled()) {
            val bm = BiometricManager.from(this)
            when (bm.canAuthenticate(BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS -> {
                    awaitingBiometricAuth = true
                    showBiometricGate()
                }
                else -> {
                    // Optional: Auto-disable to avoid repeated warnings on future launches
                    prefs.saveBiometricEnabled(false)

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
        val prompt = BiometricPrompt(this, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    awaitingBiometricAuth = false
                    Toast.makeText(this@MainActivity, "Authentication error", Toast.LENGTH_SHORT).show()
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(this@MainActivity, "Authentication failed", Toast.LENGTH_SHORT).show()

                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    awaitingBiometricAuth = false
                    ensureServiceRunning()   // service starts only after unlock
                    continueOnCreate()
                }
            })

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock oxproxion")
            .setSubtitle("Use your biometric credential")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(info)
    }

    private fun continueOnCreate() {
        setContentView(R.layout.activity_main)
        askNotificationPermission()

        prefs.seedDefaultModelsIfNeeded()
        prefs.seedDefaultSystemMessagesIfNeeded()
        prefs.migrateNotiActionsIfNeeded()
        if (!prefs.hasMigratedMaverick()) {
            // 1. Swap the active model if it was Maverick
            val currentSavedModel = prefs.getPreferenceModelnew()
            if (currentSavedModel == "meta-llama/llama-4-maverick") {
                prefs.savePreferenceModelnewchat("openrouter/free")
            }

            // 2. Scrub any old custom "openrouter/free" entries to prevent duplicates
            val customModels = prefs.getCustomModels()
            val initialSize = customModels.size

            // Remove any custom model matching the identifier (ignoring case just in case)
            customModels.removeAll { it.apiIdentifier.equals("openrouter/free", ignoreCase = true) }

            // If we actually deleted something, save the clean list back to SharedPreferences
            if (customModels.size < initialSize) {
                prefs.saveCustomModels(customModels)
            }

            // 3. Mark as complete so this never runs again
            prefs.setMigratedMaverick()
        }

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
                .add(R.id.fragment_container, chatFragment, "ChatFragment")
                .commitNow()
            // NEW: cold-start reader mode from notification
            if (intent?.getBooleanExtra("open_reader_mode", false) == true) {
                chatFragment.enterReaderModeFromNotification()
            }
        }
        // NEW: Auto‑apply "digital assistant" preset for assistant launches
        if (intent?.action in listOf(Intent.ACTION_ASSIST, Intent.ACTION_VOICE_COMMAND)) {
            val digitalAssistantPreset = findDigitalAssistantPreset()
            if (digitalAssistantPreset != null) {
                val vm: ChatViewModel by viewModels()
                PresetManager.applyPreset(this, vm, digitalAssistantPreset)
                vm.signalPresetApplied()
            }
        }
        val vm: ChatViewModel by viewModels()
        handlePresetIntent(intent)
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

                val systemMessageTitle = prefs.getSelectedSystemMessage().title
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

    }
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment is OnKeyboardShortcutListener) {
            // We pass the event back into handleKeyDown, but the Fragment's 'isLongPress' check
            // will now be 'true' because the system successfully tracked it.
            if (currentFragment.handleKeyDown(keyCode, event)) {
                return true
            }
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            event?.startTracking() // Required for isLongPress to work in the Fragment!
        }
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) // Or however you get your current fragment

        if (currentFragment is OnKeyboardShortcutListener) {
            if (currentFragment.handleKeyDown(keyCode, event)) {
                return true // The fragment handled the shortcut
            }
        }

        // 2. If the fragment didn't handle it, or no fragment is active,
        //    handle it in the Activity (for global app shortcuts)


        // Let the system handle unhandled keys (e.g., volume, back button)
        return super.onKeyDown(keyCode, event)
    }



    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("from_notification", false)) {
            val chatFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? ChatFragment
            if (intent.getBooleanExtra("open_reader_mode", false)) {
                chatFragment?.enterReaderModeFromNotification()
            } else {
                chatFragment?.onOpenedFromNotification()
            }
        }
        handlePresetIntent(intent)

        if (intent.action == Intent.ACTION_SEND && "text/plain" == intent.type && !intent.getBooleanExtra("autosend", false)) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                val vm: ChatViewModel by viewModels()

                vm.consumeSharedText(text)
                val systemMessageTitle = prefs.getSelectedSystemMessage().title
                Toast.makeText(this, systemMessageTitle, Toast.LENGTH_SHORT).show()
                //Toast.makeText(this, "Text received", Toast.LENGTH_LONG).show()
                //val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                //if (fragment is ChatFragment) {
                //    fragment.setSharedText(text)
                //  }
            }

        }

        /*  if (intent.getBooleanExtra("autosend", false)) {
              intent.getStringExtra("shared_text")?.let { text ->
                  val vm: ChatViewModel by viewModels()
                  val sharedPreferencesHelper = SharedPreferencesHelper(this)
                  val clearChat = intent.getBooleanExtra("clear_chat", false)
                  if (clearChat) {
                      vm.startNewChat()  // Clear the chat
                  }
                  vm.consumeSharedTextautosend(text)
                  val systemMessageTitle = sharedPreferencesHelper.getSelectedSystemMessage().title
                  Toast.makeText(this, "System: $systemMessageTitle", Toast.LENGTH_SHORT).show()
              }
          }*/
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

                val systemMessageTitle = prefs.getSelectedSystemMessage().title
                Toast.makeText(this, systemMessageTitle, Toast.LENGTH_SHORT).show()
            }
        }
        val isAssistLaunch = intent.action in listOf(Intent.ACTION_ASSIST)
        if (isAssistLaunch) {
            val digitalAssistantPreset = findDigitalAssistantPreset()
            if (digitalAssistantPreset != null) {
                val vm: ChatViewModel by viewModels()
                PresetManager.applyPreset(this, vm, digitalAssistantPreset)
                vm.signalPresetApplied()
            }
            val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? ChatFragment
            fragment?.startSpeechRecognitionSafely()  // Custom method below
        }
    }

    private fun findDigitalAssistantPreset(): Preset? {
        val repository = PresetRepository(this)
        val allPresets = repository.getAll()
        return allPresets.find { preset ->
            preset.title.lowercase().trim() == "digital assistant"
        }
    }
    private fun handlePresetIntent(intent: Intent) {
        if (intent.getBooleanExtra("apply_preset", false)) {
            val presetId = intent.getStringExtra("preset_id") ?: return

            // 1. Fetch the full Preset object from Repository
            val repository = PresetRepository(this)
            val preset = repository.findById(presetId)

            if (preset == null) {
                Toast.makeText(this, "Preset not found (ID: $presetId)", Toast.LENGTH_LONG).show()
                return
            }

            val vm: ChatViewModel by viewModels()


            // 2. Validation (Model and System Message still exist)
            val allModels = (vm.getBuiltInModels() + prefs.getCustomModels()).distinctBy { it.apiIdentifier.lowercase() }
            if (allModels.none { it.apiIdentifier.equals(preset.modelIdentifier, ignoreCase = true) }) {
                Toast.makeText(this, "Preset not applied: Model \"${preset.modelIdentifier}\" no longer exists.", Toast.LENGTH_LONG).show()
                return
            }

            val allMessages = listOf(prefs.getDefaultSystemMessage()) + prefs.getCustomSystemMessages()
            if (allMessages.none { it.title == preset.systemMessage.title && it.prompt == preset.systemMessage.prompt }) {
                Toast.makeText(this, "Preset not applied: System message \"${preset.systemMessage.title}\" no longer exists.", Toast.LENGTH_LONG).show()
                return
            }

            // 3. Apply the Preset (This now includes Web Search via PresetManager)
            PresetManager.applyPreset(this, vm, preset)
            vm.signalPresetApplied()

            // 4. Handle the shared text
            val sharedText = intent.getStringExtra("shared_text")
            if (sharedText != null) {
                if (intent.getBooleanExtra("clear_chat", false)) {
                    vm.startNewChat()
                }

                if (intent.getBooleanExtra("autosend_preset", false)) {
                    vm.consumeSharedTextautosend(sharedText)
                } else if (intent.getBooleanExtra("input_only_preset", false)) {
                    vm.consumeSharedText(sharedText)
                }
            }

            // Clear the flag to prevent re-applying on rotation
            intent.removeExtra("apply_preset")
        }
    }
    private fun ensureServiceRunning() {
        if (!ForegroundService.isRunningForeground)
        {
            val intent = Intent(this, ForegroundService::class.java)
            startForegroundService(intent)
        }
    }
}
