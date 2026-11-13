package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        // ---- Categories ----
        view.findViewById<View>(R.id.cardVoice).setOnClickListener {
            openCategory(VoiceSettingsFragment())
        }

        view.findViewById<View>(R.id.cardChatAppearance).setOnClickListener {
            openCategory(ChatAppearanceSettingsFragment())
        }
        // AI BEHAVIOR
        view.findViewById<View>(R.id.cardAiBehavior).setOnClickListener {
            openCategory(AiBehaviorSettingsFragment())
        }
        // NETWORK & API
        view.findViewById<View>(R.id.cardNetwork).setOnClickListener {
            openCategory(NetworkSettingsFragment())
        }
        // SECURITY
        view.findViewById<View>(R.id.cardSecurity).setOnClickListener {
            openCategory(SecuritySettingsFragment())
        }
        // HELP — opens HelpFragment directly, no category page needed
        view.findViewById<View>(R.id.cardHelp).setOnClickListener {
            parentFragmentManager.beginTransaction()
                /*  .setCustomAnimations(
                      R.anim.slide_in_right, R.anim.fade_out,
                      R.anim.fade_in, R.anim.slide_out_right
                  )*/
                .hide(this)
                .add(R.id.fragment_container, HelpFragment())
                .addToBackStack(null)
                .commit()
        }
// DISPLAY & THEME
        view.findViewById<View>(R.id.cardDisplay).setOnClickListener {
            openCategory(DisplaySettingsFragment())
        }
// NOTIFICATIONS
        view.findViewById<View>(R.id.cardNotifications).setOnClickListener {
            openCategory(NotificationSettingsFragment())
        }

    }

    private fun openCategory(f: Fragment) {
        parentFragmentManager.beginTransaction()
            /* .setCustomAnimations(
                 R.anim.slide_in_right, R.anim.fade_out,
                 R.anim.fade_in, R.anim.slide_out_right
             )*/
            .hide(this)
            .add(R.id.fragment_container, f)
            .addToBackStack(null)
            .commit()
    }
}
