package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class AiBehaviorSettingsFragment : Fragment(R.layout.fragment_settings_ai_behavior) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val prefs = SharedPreferencesHelper(requireContext())

        val inferenceParamsButton = view.findViewById<MaterialButton>(R.id.inferenceParamsButton)
        val chatMemoryButton = view.findViewById<MaterialButton>(R.id.chatMemoryButton)
        val promptsButton = view.findViewById<MaterialButton>(R.id.promptsButton)
        val toolsButton = view.findViewById<MaterialButton>(R.id.toolsButton)
        val openRouterTransformsSwitch = view.findViewById<MaterialSwitch>(R.id.openRouterTransformsSwitch)
        val autoDisableWebSearchSwitch = view.findViewById<MaterialSwitch>(R.id.autoDisableWebSearchSwitch)

        // ---- Load current state ----
        val memoryCount = prefs.getChatMemoryCount()
        chatMemoryButton.text = if (memoryCount == Int.MAX_VALUE) "All messages" else "$memoryCount messages"
        openRouterTransformsSwitch.isChecked = prefs.getOpenRouterTransformsEnabled()
        autoDisableWebSearchSwitch.isChecked = prefs.getDisableWebSearchAfterSend()

        // ---- Listeners (verbatim from your original) ----
        inferenceParamsButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .hide(this)
                .add(R.id.fragment_container, InferenceParametersFragment())
                .addToBackStack(null)
                .commit()
        }

        chatMemoryButton.setOnClickListener {
            val dialog = ChatMemoryDialogFragment()
            dialog.onMemoryChanged = { count ->
                chatMemoryButton.text = if (count == Int.MAX_VALUE) "All messages" else "$count messages"
            }
            dialog.show(childFragmentManager, "ChatMemoryDialogFragment")
        }


        promptsButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .hide(this)
                .add(R.id.fragment_container, PromptLibraryFragment())
                .addToBackStack(null)
                .commit()
        }

        toolsButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .hide(this)
                .add(R.id.fragment_container, ToolsFragment())
                .addToBackStack(null)
                .commit()
        }

        openRouterTransformsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveOpenRouterTransformsEnabled(isChecked)
        }

        autoDisableWebSearchSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveDisableWebSearchAfterSend(isChecked)
        }

        // ---- Style switches ----
        styleAllSwitches(
            view,
            R.id.openRouterTransformsSwitch,
            R.id.autoDisableWebSearchSwitch
        )
    }
}
