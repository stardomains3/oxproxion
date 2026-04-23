package io.github.stardomains3.oxproxion
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity

class AssistantActivity : AppCompatActivity() {

    private var stateSnapshot: AssistantStateSnapshot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val vm: ChatViewModel by viewModels()

        // 1. Capture current state BEFORE applying preset
        stateSnapshot = PresetManager.captureCurrentState(this, vm)

        // 2. Apply the assistant preset
        val preset = findDigitalAssistantPreset()
        if (preset != null) {
            PresetManager.applyPreset(this, vm, preset)
            vm.signalPresetApplied()
        }

        // 3. Load fragment
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            val chatFragment = ChatFragment().apply {
                arguments = Bundle().apply {
                    putBoolean("start_stt_on_launch", true)
                }
            }
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, chatFragment, "ChatFragment")
                .commitNow()
        }
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Restore original state when assistant is destroyed
        stateSnapshot?.let { snapshot ->
            val vm: ChatViewModel by viewModels()
            PresetManager.restoreState(this, vm, snapshot)
        }
    }

    private fun findDigitalAssistantPreset(): Preset? {
        val repository = PresetRepository(this)
        return repository.getAll().find {
            it.title.lowercase().trim() == "digital assistant"
        }
    }
}