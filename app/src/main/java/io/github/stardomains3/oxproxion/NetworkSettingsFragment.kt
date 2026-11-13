package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class NetworkSettingsFragment : Fragment(R.layout.fragment_settings_network) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val viewModel: ChatViewModel by activityViewModels()

        val lanButton = view.findViewById<MaterialButton>(R.id.lanButton)
        val timeoutButton = view.findViewById<MaterialButton>(R.id.timeoutButton)
        val apiKeyButton = view.findViewById<MaterialButton>(R.id.apiKeyButton)
        val braveApiKeyButton = view.findViewById<MaterialButton>(R.id.braveApiKeyButton)
        val creditsButton = view.findViewById<MaterialButton>(R.id.creditsButton)

        // ---- LAN Settings ----
        lanButton.setOnClickListener {
            SaveLANDialogFragment().show(childFragmentManager, SaveLANDialogFragment.TAG)
        }

        // ---- Request Timeout ----
        timeoutButton.setOnClickListener {
            TimeoutDialogFragment().show(childFragmentManager, TimeoutDialogFragment.TAG)
        }

        // ---- OpenRouter API Key ----
        apiKeyButton.setOnClickListener {
            SaveApiDialogFragment().show(childFragmentManager, "SaveApiDialogFragment")
        }

        // ---- Brave Search API Key ----
        braveApiKeyButton.setOnClickListener {
            SaveBraveApiDialogFragment().show(childFragmentManager, SaveBraveApiDialogFragment.TAG)
        }

        // ---- Check Remaining Credits ----
        // Original behavior: leave settings entirely, then query.
        // We're now one level deeper (settings → Network), so pop TWICE:
        // Network → Settings → Chat.
        creditsButton.setOnClickListener {
            if (viewModel.activeChatApiKey.isBlank()) {
                Toast.makeText(requireContext(), "API Key is not set.", Toast.LENGTH_SHORT).show()
            } else {
                parentFragmentManager.popBackStack() // Network → Settings
                parentFragmentManager.popBackStack() // Settings → Chat
                viewModel.checkRemainingCredits()
            }
        }
    }
}
