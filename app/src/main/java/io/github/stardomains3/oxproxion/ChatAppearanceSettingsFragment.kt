package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch

class ChatAppearanceSettingsFragment : Fragment(R.layout.fragment_settings_chat_appearance) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val prefs = SharedPreferencesHelper(requireContext())
        val viewModel: ChatViewModel by activityViewModels()
        val convoModeSwitch = view.findViewById<MaterialSwitch>(R.id.convoModeSwitch)

        val streamingSwitch = view.findViewById<MaterialSwitch>(R.id.streamingSwitch)
        val extendedTopBarSwitch = view.findViewById<MaterialSwitch>(R.id.extendedTopBarSwitch)
        val extendedDockSwitch = view.findViewById<MaterialSwitch>(R.id.extendedDockSwitch)
        val expandableInputSwitch = view.findViewById<MaterialSwitch>(R.id.expandableInputSwitch)
        val presetsExtendedSwitch = view.findViewById<MaterialSwitch>(R.id.presetsExtendedSwitch)
        val scrollProgressSwitch = view.findViewById<MaterialSwitch>(R.id.scrollProgressSwitch)
        val scrollButtonsSwitch = view.findViewById<MaterialSwitch>(R.id.scrollButtonsSwitch)
        val volumeScrollSwitch = view.findViewById<MaterialSwitch>(R.id.volumeScrollSwitch)
        val animateBarOnErrorSwitch = view.findViewById<MaterialSwitch>(R.id.animateBarOnErrorSwitch)
        val showCitationsSwitch = view.findViewById<MaterialSwitch>(R.id.showCitationsSwitch)

        // ---- Load current state ----
        streamingSwitch.isChecked = viewModel.isStreamingEnabled.value ?: false
        extendedTopBarSwitch.isChecked = prefs.getExtendedTopBarEnabled()
        animateBarOnErrorSwitch.isChecked = prefs.getAnimateBarOnError()
        showCitationsSwitch.isChecked = prefs.getShowCitations()
        scrollButtonsSwitch.isChecked = viewModel.isScrollersEnabled.value ?: false
        volumeScrollSwitch.isChecked = viewModel.isVolumeScrollEnabled.value ?: false
        expandableInputSwitch.isChecked = viewModel.isExpandableInputEnabled.value ?: false
        extendedDockSwitch.isChecked = viewModel.isExtendedDockEnabled.value ?: false
        presetsExtendedSwitch.isChecked = viewModel.isPresetsExtendedEnabled.value ?: false
        scrollProgressSwitch.isChecked = viewModel.isScrollProgressEnabled.value ?: true
        convoModeSwitch.isChecked = prefs.getConversationModeEnabled()


        // ---- Streaming (moved from chat menu, Lyria guard preserved) ----
        streamingSwitch.setOnCheckedChangeListener { _, isChecked ->
            // ECHO GUARD: skip events that merely reflect the VM's current state
            // (observer-driven updates, and our own Lyria revert below).
            val current = viewModel.isStreamingEnabled.value ?: false
            if (isChecked == current) return@setOnCheckedChangeListener

            val model = viewModel.activeChatModel.value ?: ""
            val isLyria = model.contains("google/lyria", ignoreCase = true)
            if (isLyria && !isChecked) {
                // Refuse: revert the switch visually WITHOUT toggling the VM.
                // Setting isChecked re-fires this listener, but then isChecked == current
                // and the echo guard above swallows it. No loop, no accidental toggle.
                streamingSwitch.isChecked = true
                Toast.makeText(
                    requireContext(),
                    "Streaming is required for Lyria music models.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                viewModel.toggleStreaming()  // VM saves + notifies Chat
            }
        }
        viewModel.isStreamingEnabled.observe(viewLifecycleOwner) { enabled ->
            streamingSwitch.isChecked = enabled
        }

        // ---- Everything below: unchanged from your working version ----
        extendedTopBarSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleExtendedTopBar()
        }

        extendedDockSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleExtendedDock()
        }

        expandableInputSwitch.setOnCheckedChangeListener { _, _ ->
            viewModel.toggleExpandableInput()
        }
        convoModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveConversationModeEnabled(isChecked)
        }

        presetsExtendedSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.togglePresetsExtended()
        }
        viewModel.isPresetsExtendedEnabled.observe(viewLifecycleOwner) { enabled ->
            presetsExtendedSwitch.isChecked = enabled
        }

        scrollProgressSwitch.setOnCheckedChangeListener { _, _ ->
            viewModel.toggleScrollProgress()
        }
        viewModel.isScrollProgressEnabled.observe(viewLifecycleOwner) { enabled ->
            scrollProgressSwitch.isChecked = enabled
        }

        scrollButtonsSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleScrollers()
        }

        volumeScrollSwitch.setOnCheckedChangeListener { _, _ ->
            viewModel.toggleVolumeScroll()
        }
        viewModel.isVolumeScrollEnabled.observe(viewLifecycleOwner) { enabled ->
            volumeScrollSwitch.isChecked = enabled
        }

        animateBarOnErrorSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveAnimateBarOnError(isChecked)
        }

        showCitationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveShowCitations(isChecked)
        }

        // ---- Style all switches ----
        styleAllSwitches(
            view,
            R.id.streamingSwitch,
            R.id.extendedTopBarSwitch,
            R.id.extendedDockSwitch,
            R.id.expandableInputSwitch,
            R.id.presetsExtendedSwitch,
            R.id.scrollProgressSwitch,
            R.id.scrollButtonsSwitch,
            R.id.volumeScrollSwitch,
            R.id.animateBarOnErrorSwitch,
            R.id.showCitationsSwitch,
            R.id.convoModeSwitch

        )
    }
}
