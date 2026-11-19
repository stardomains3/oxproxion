package io.github.stardomains3.oxproxion

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.core.graphics.toColorInt
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class PermissionsSettingsFragment : Fragment(R.layout.fragment_settings_permissions) {

    companion object {
        // Not a public constant in the Manifest class — use the literal like ChatFragment does
        private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
        private const val COLOR_GRANTED = "#007541"
        private const val COLOR_DENIED = "#8c1911"
    }

    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private var pendingPermission: String? = null

    private val permissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val perm = pendingPermission
            pendingPermission = null
            refreshPermissionStates()
            if (!granted && perm != null) {
                if (!shouldShowRequestPermissionRationale(perm)) {
                    // Permanently denied — only fixable via system App Settings
                    Toast.makeText(
                        requireContext(),
                        "Permission permanently denied. Use \"Android App Settings\" below to enable it.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val folderPickerLauncher: ActivityResultLauncher<Uri?> =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)
                sharedPreferencesHelper.saveSafFolderUri(uri.toString())
                Toast.makeText(requireContext(), "Folder updated! Tools should now work.", Toast.LENGTH_SHORT).show()
            }
            refreshPermissionStates()
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        sharedPreferencesHelper = SharedPreferencesHelper(requireContext())

        // ---- Runtime permission buttons ----
        view.findViewById<MaterialButton>(R.id.micGrantButton).setOnClickListener {
            requestPermission(Manifest.permission.RECORD_AUDIO)
        }
        view.findViewById<MaterialButton>(R.id.cameraGrantButton).setOnClickListener {
            requestPermission(Manifest.permission.CAMERA)
        }
        view.findViewById<MaterialButton>(R.id.locationGrantButton).setOnClickListener {
            requestPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        view.findViewById<MaterialButton>(R.id.localNetworkGrantButton).setOnClickListener {
            requestPermission(ACCESS_LOCAL_NETWORK)
        }

        // ---- Do Not Disturb access (special permission, granted in system settings) ----
        view.findViewById<MaterialButton>(R.id.dndGrantButton).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(requireContext(), "Screen not available on this device", Toast.LENGTH_SHORT).show()
            }
        }

        // ---- SAF folder fix (same launcher seed as HelpFragment) ----
        view.findViewById<MaterialButton>(R.id.safGrantButton).setOnClickListener {
            val downloadsUri =
                "content://com.android.externalstorage.documents/document/primary%3ADownload".toUri()
            folderPickerLauncher.launch(downloadsUri)
        }

        // ---- Battery optimization (unrestricted) ----
        view.findViewById<MaterialButton>(R.id.batteryGrantButton).setOnClickListener {
            openBatteryOptimizationSettings()
        }

        // ---- Catch-all: system App Settings screen ----
        view.findViewById<MaterialButton>(R.id.appSettingsButton).setOnClickListener {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${requireContext().packageName}".toUri()
            )
            startActivity(intent)
        }

        refreshPermissionStates()
    }

    override fun onResume() {
        super.onResume()
        // Refresh statuses after returning from system settings screens
        // (DND access, battery optimization, app settings are all granted outside the app)
        refreshPermissionStates()
    }

    private fun requestPermission(permission: String) {
        pendingPermission = permission
        permissionLauncher.launch(permission)
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), permission) ==
                PackageManager.PERMISSION_GRANTED

    private fun refreshPermissionStates() {
        val context = requireContext()

        // 1-4: runtime permissions
        updatePermissionRow(
            view?.findViewById(R.id.micStatus),
            view?.findViewById(R.id.micGrantButton),
            isGranted(Manifest.permission.RECORD_AUDIO)
        )
        updatePermissionRow(
            view?.findViewById(R.id.cameraStatus),
            view?.findViewById(R.id.cameraGrantButton),
            isGranted(Manifest.permission.CAMERA)
        )
        updatePermissionRow(
            view?.findViewById(R.id.locationStatus),
            view?.findViewById(R.id.locationGrantButton),
            isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        )

        // Local network: only relevant on Android 16+ (API 37)
        val localNetworkRow = view?.findViewById<LinearLayout>(R.id.localNetworkRow)
        if (Build.VERSION.SDK_INT >= 37) {
            localNetworkRow?.isVisible = true
            updatePermissionRow(
                view?.findViewById(R.id.localNetworkStatus),
                view?.findViewById(R.id.localNetworkGrantButton),
                isGranted(ACCESS_LOCAL_NETWORK)
            )
        } else {
            localNetworkRow?.isVisible = false
        }

        // Do Not Disturb access
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val dndGranted = notificationManager?.isNotificationPolicyAccessGranted == true
        updatePermissionRow(
            view?.findViewById(R.id.dndStatus),
            view?.findViewById(R.id.dndGrantButton),
            dndGranted
        )

        // SAF folder
        val safGranted = hasSafFolderPermission()
        view?.findViewById<TextView>(R.id.safStatus)?.apply {
            text = if (safGranted) "Granted" else "Not set"
            setTextColor((if (safGranted) COLOR_GRANTED else COLOR_DENIED).toColorIntCompat())
        }
        // SAF button always visible — user may need to re-select a wrong folder even if one is set

        // Battery optimization
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val ignoringBattery =
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        view?.findViewById<TextView>(R.id.batteryStatus)?.apply {
            text = if (ignoringBattery) "Unrestricted" else "Restricted"
            setTextColor((if (ignoringBattery) COLOR_GRANTED else COLOR_DENIED).toColorIntCompat())
        }
        // Button always visible so the user can review/change it

        // App settings button always visible
    }

    private fun updatePermissionRow(statusView: TextView?, grantButton: MaterialButton?, granted: Boolean) {
        statusView?.apply {
            text = if (granted) "Granted" else "Denied"
            setTextColor((if (granted) COLOR_GRANTED else COLOR_DENIED).toColorIntCompat())
        }
        grantButton?.isVisible = !granted
    }

    /** Same logic as ChatFragment.hasFolderPermission() */
    private fun hasSafFolderPermission(): Boolean {
        val uriString = sharedPreferencesHelper.getSafFolderUri() ?: return false
        val treeUri = uriString.toUri()
        return requireContext().contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission
        }
    }

    private fun openBatteryOptimizationSettings() {
        // Try the direct "ignore battery optimizations" request dialog for THIS app first
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:${requireContext().packageName}".toUri()
            )
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Fallback: the full list of optimized apps
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: ActivityNotFoundException) {
                // Last resort: general app details screen (battery settings reachable from there)
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${requireContext().packageName}".toUri()
                )
                startActivity(intent)
            }
        }
    }

    private fun String.toColorIntCompat(): Int = this.toColorInt()

}
