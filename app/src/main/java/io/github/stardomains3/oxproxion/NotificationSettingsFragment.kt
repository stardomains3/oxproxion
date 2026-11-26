package io.github.stardomains3.oxproxion

import android.content.Intent
import android.provider.Settings
import com.google.android.material.button.MaterialButton
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch

class NotificationSettingsFragment : Fragment(R.layout.fragment_settings_notifications) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val prefs = SharedPreferencesHelper(requireContext())

        val notificationsSwitch = view.findViewById<MaterialSwitch>(R.id.notificationsSwitch)
        val slot1ChipGroup = view.findViewById<ChipGroup>(R.id.slot1ChipGroup)
        val slot2ChipGroup = view.findViewById<ChipGroup>(R.id.slot2ChipGroup)
        val systemNotiSettingsButton =
            view.findViewById<MaterialButton>(R.id.systemNotiSettingsButton)


        // ---- Load current state ----
        notificationsSwitch.isChecked = prefs.getNotiPreference()



        // ---- Listeners (verbatim from your original) ----
        notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveNotiPreference(isChecked)
        }
// ---- Load current state ----
        slot1ChipGroup.check(
            when (prefs.getNotiActionSlot1()) {
                SharedPreferencesHelper.NOTI_ACTION_COPY    -> R.id.slot1CopyChip
                SharedPreferencesHelper.NOTI_ACTION_OPEN    -> R.id.slot1OpenChip
                SharedPreferencesHelper.NOTI_ACTION_READER   -> R.id.slot1ReaderChip
                else                                        -> R.id.slot1DismissChip
            }
        )
        slot2ChipGroup.check(
            when (prefs.getNotiActionSlot2()) {
                SharedPreferencesHelper.NOTI_ACTION_COPY    -> R.id.slot2CopyChip
                SharedPreferencesHelper.NOTI_ACTION_OPEN    -> R.id.slot2OpenChip
                SharedPreferencesHelper.NOTI_ACTION_READER   -> R.id.slot2ReaderChip
                else                                        -> R.id.slot2DismissChip
            }
        )
        // ---- Save on change ----
        slot1ChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                prefs.saveNotiActionSlot1(
                    when (checkedIds.first()) {
                        R.id.slot1CopyChip  -> SharedPreferencesHelper.NOTI_ACTION_COPY
                        R.id.slot1OpenChip  -> SharedPreferencesHelper.NOTI_ACTION_OPEN
                        R.id.slot1ReaderChip -> SharedPreferencesHelper.NOTI_ACTION_READER
                        else                -> SharedPreferencesHelper.NOTI_ACTION_DISMISS
                    }
                )
            }
        }

        slot2ChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                prefs.saveNotiActionSlot2(
                    when (checkedIds.first()) {
                        R.id.slot2CopyChip  -> SharedPreferencesHelper.NOTI_ACTION_COPY
                        R.id.slot2OpenChip  -> SharedPreferencesHelper.NOTI_ACTION_OPEN
                        R.id.slot2ReaderChip -> SharedPreferencesHelper.NOTI_ACTION_READER
                        else                -> SharedPreferencesHelper.NOTI_ACTION_DISMISS
                    }
                )
            }
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
            R.id.notificationsSwitch
        )
    }
}


