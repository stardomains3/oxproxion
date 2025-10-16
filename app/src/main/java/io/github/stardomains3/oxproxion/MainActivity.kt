package io.github.stardomains3.oxproxion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.getValue

class MainActivity : AppCompatActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // We can handle the result here if needed in the future
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        askNotificationPermission()
        val sharedPreferencesHelper = SharedPreferencesHelper(this)
        sharedPreferencesHelper.seedDefaultModelsIfNeeded()
        sharedPreferencesHelper.seedDefaultSystemMessagesIfNeeded()

        if (savedInstanceState == null) {
            val chatFragment = ChatFragment()
            if (intent?.action == Intent.ACTION_SEND && "text/plain" == intent.type) {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                    val bundle = Bundle()
                    bundle.putString("shared_text", it)
                    chatFragment.arguments = bundle
                }
            }
            val isAssistLaunch = intent?.action in listOf(Intent.ACTION_ASSIST, Intent.ACTION_VOICE_COMMAND)
            if (isAssistLaunch) {
                val bundle = chatFragment.arguments ?: Bundle()
                bundle.putBoolean("start_stt_on_launch", true)
                chatFragment.arguments = bundle
            }
            supportFragmentManager.beginTransaction()
               // .replace(R.id.fragment_container, chatFragment)
                .add(R.id.fragment_container, chatFragment)
                .commitNow()

        }
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                val handled = (fragment as? ChatFragment)?.onBackPressed() ?: false

                if (!handled) {
                    if (supportFragmentManager.backStackEntryCount == 0) {
                        // No fragments on the back stack, so we are on the main screen.
                        // Move the app to the background.
                        moveTaskToBack(true)
                    } else {
                        // There are fragments on the back stack, so let the default behavior happen.
                        // This will pop the fragment.
                        // We temporarily disable this callback and trigger the back press again.
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
        if(sharedPreferencesHelper.getNotiPreference()) {
            startForegroundService()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_SEND && "text/plain" == intent.type) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                val vm: ChatViewModel by viewModels()
                vm.consumeSharedText(text)
                //Toast.makeText(this, "Text received", Toast.LENGTH_LONG).show()
                //val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                //if (fragment is ChatFragment) {
                //    fragment.setSharedText(text)
                //  }
            }
        }
        val isAssistLaunch = intent.action in listOf(Intent.ACTION_ASSIST)
        if (isAssistLaunch) {
            val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? ChatFragment
            fragment?.startSpeechRecognitionSafely()  // Custom method below
        }
    }
    private fun startForegroundService() {
        if (ForegroundService.isRunningForeground) return  // Guard to prevent restarts
        try {
            val serviceIntent = Intent(this, ForegroundService::class.java)
            // Optionally pass initial title if needed
            val vm: ChatViewModel by viewModels()
            val displayName = vm.getModelDisplayName(vm.activeChatModel.value ?: "Unknown Model")
            serviceIntent.putExtra("initial_title", displayName)
            startService(serviceIntent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start foreground service", e)
        }
    }
    private fun askNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
