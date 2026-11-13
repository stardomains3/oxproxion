package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.View
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
        extendedTopBarSwitch.isChecked = prefs.getExtendedTopBarEnabled()
        animateBarOnErrorSwitch.isChecked = prefs.getAnimateBarOnError()
        showCitationsSwitch.isChecked = prefs.getShowCitations()
        scrollButtonsSwitch.isChecked = viewModel.isScrollersEnabled.value ?: false
        volumeScrollSwitch.isChecked = viewModel.isVolumeScrollEnabled.value ?: false
        expandableInputSwitch.isChecked = viewModel.isExpandableInputEnabled.value ?: false
        extendedDockSwitch.isChecked = viewModel.isExtendedDockEnabled.value ?: false
        presetsExtendedSwitch.isChecked = viewModel.isPresetsExtendedEnabled.value ?: false
        scrollProgressSwitch.isChecked = viewModel.isScrollProgressEnabled.value ?: true

        // ---- Listeners (verbatim from your original) ----
        extendedTopBarSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleExtendedTopBar()  // VM saves + notifies Chat
        }
        extendedTopBarSwitch.isChecked = prefs.getExtendedTopBarEnabled() // keep synced

        extendedDockSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleExtendedDock()  // VM saves + notifies Chat
        }

        expandableInputSwitch.setOnCheckedChangeListener { _, _ ->
            viewModel.toggleExpandableInput()
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
            viewModel.toggleScrollers()  // VM saves prefs + notifies Chat instantly
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
            R.id.extendedTopBarSwitch,
            R.id.extendedDockSwitch,
            R.id.expandableInputSwitch,
            R.id.presetsExtendedSwitch,
            R.id.scrollProgressSwitch,
            R.id.scrollButtonsSwitch,
            R.id.volumeScrollSwitch,
            R.id.animateBarOnErrorSwitch,
            R.id.showCitationsSwitch
        )
    }
}
