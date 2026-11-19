package io.github.stardomains3.oxproxion

import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
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


        // ---- Load current state ----
        notificationsSwitch.isChecked = prefs.getNotiPreference()
        copyOrDismissSwitch.isChecked = prefs.getUseCopyButton2()
        copyOrOpenSwitch.isChecked = prefs.getUseCopyButton()
        val systemNotiSettingsButton =
            view.findViewById<MaterialButton>(R.id.systemNotiSettingsButton)

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

        // ---- Open the system's App Notification Settings screen ----
        systemNotiSettingsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            startActivity(intent)
        }

        // ---- Style switches ----
        styleAllSwitches(
            view,
            R.id.notificationsSwitch,
            R.id.copyOrdismissSwitch,
            R.id.copyOropenSwitch
        )
    }
}

