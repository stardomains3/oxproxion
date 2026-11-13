package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch

class SecuritySettingsFragment : Fragment(R.layout.fragment_settings_security) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val prefs = SharedPreferencesHelper(requireContext())
        val biometricsSwitch = view.findViewById<MaterialSwitch>(R.id.biometricsSwitch)

        // ---- Load current state ----
        biometricsSwitch.isChecked = prefs.getBiometricEnabled()

        // ---- Listener (verbatim from your original) ----
        biometricsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val bm = BiometricManager.from(requireContext())
                when (bm.canAuthenticate(BIOMETRIC_STRONG)) {
                    BiometricManager.BIOMETRIC_SUCCESS -> {
                        prefs.saveBiometricEnabled(true)
                    }
                    else -> {
                        biometricsSwitch.isChecked = false
                        Toast.makeText(requireContext(), "No biometrics available", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                prefs.saveBiometricEnabled(false)
            }
        }

        // ---- Style switch ----
        styleAllSwitches(view, R.id.biometricsSwitch)
    }
}
