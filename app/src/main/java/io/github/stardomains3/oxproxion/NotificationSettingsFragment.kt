package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch

class NotificationSettingsFragment : Fragment(R.layout.fragment_settings_notifications) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val prefs = SharedPreferencesHelper(requireContext())

        val notificationsSwitch = view.findViewById<MaterialSwitch>(R.id.notificationsSwitch)
        val copyOrDismissSwitch = view.findViewById<MaterialSwitch>(R.id.copyOrdismissSwitch)
        val copyOrOpenSwitch = view.findViewById<MaterialSwitch>(R.id.copyOropenSwitch)
        val autoBackSwitch = view.findViewById<MaterialSwitch>(R.id.autoBackSwitch)

        // ---- Load current state ----
        notificationsSwitch.isChecked = prefs.getNotiPreference()
        copyOrDismissSwitch.isChecked = prefs.getUseCopyButton2()
        copyOrOpenSwitch.isChecked = prefs.getUseCopyButton()
        autoBackSwitch.isChecked = prefs.getAutoBack()

        // ---- Listeners (verbatim from your original) ----
        notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveNotiPreference(isChecked)
        }

        copyOrDismissSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveUseCopyButton2(isChecked)  // true = Copy button, false = Dismiss button
        }

        copyOrOpenSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveUseCopyButton(isChecked)  // true = Copy button, false = Open button
        }

        autoBackSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveAutoBack(isChecked)
        }

        // ---- Style switches ----
        styleAllSwitches(
            view,
            R.id.notificationsSwitch,
            R.id.copyOrdismissSwitch,
            R.id.copyOropenSwitch,
            R.id.autoBackSwitch
        )
    }
}
