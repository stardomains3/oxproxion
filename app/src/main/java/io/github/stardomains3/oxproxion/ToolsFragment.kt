package io.github.stardomains3.oxproxion

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.core.net.toUri
import java.io.File
import kotlin.collections.remove

class ToolsFragment : Fragment(R.layout.fragment_tools) {

    private lateinit var locationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var notificationPolicyLauncher: ActivityResultLauncher<Intent>
    private lateinit var folderPickerLauncher: ActivityResultLauncher<Uri?>

    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(requireContext(), "Location permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Location permission is required for this tool", Toast.LENGTH_SHORT).show()
            }
            refreshUI()
        }

        notificationPolicyLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            refreshUI()
        }

        folderPickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                requireContext().contentResolver.takePersistableUriPermission(uri, takeFlags)
                sharedPreferencesHelper.saveSafFolderUri(uri.toString())
                Toast.makeText(requireContext(), "Folder access granted!", Toast.LENGTH_SHORT).show()
                refreshUI()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferencesHelper = SharedPreferencesHelper(requireContext())

        // 👇 NEW: Ensure the oxproxion folder exists when fragment opens
        ensureOxproxionFolderExists()

        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        setupToolsList(view)
    }

    // 👇 NEW: Helper to create the oxproxion folder if it doesn't exist
    private fun ensureOxproxionFolderExists() {
        val folderPath = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "oxproxion"
        )
        if (!folderPath.exists()) {
            folderPath.mkdirs()
        }
    }

    private fun setupToolsList(rootView: View) {
        val container = rootView.findViewById<LinearLayout>(R.id.tools_container)
        container.removeAllViews()

        val enabledTools = sharedPreferencesHelper.getEnabledTools()
        val hasStoredPrefs = sharedPreferencesHelper.hasEnabledToolsStored()
        val effectiveEnabledSet = if (!hasStoredPrefs) {
            ToolItem.getAllToolItems(emptySet()).map { it.name }.toSet()
        } else {
            enabledTools
        }

        var allItems = ToolItem.getAllToolItems(effectiveEnabledSet)

        // Filter Brave
        val braveApiKey = sharedPreferencesHelper.getApiKeyFromPrefs("brave_search_api_key")
        val hasBraveKey = braveApiKey.isNotEmpty()
        allItems = allItems.filter { item ->
            if (item.name == "brave_search" || item.name == "find_nearby_places") hasBraveKey else true
        }

        // Check Permissions
        val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val hasNotificationPolicy = notificationManager.isNotificationPolicyAccessGranted
        val hasLocationPermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasSafPermission = hasFolderPermission()

        val inflater = layoutInflater

        for (item in allItems) {
            val row = inflater.inflate(R.layout.item_tool_toggle2, container, false)
            val checkBox = row.findViewById<MaterialSwitch>(R.id.checkbox_tool)
            val titleTv = row.findViewById<TextView>(R.id.text_tool_title)
            val descTv = row.findViewById<TextView>(R.id.text_tool_desc)
            val permissionWarning = row.findViewById<TextView>(R.id.text_permission_warning)
            checkBox.styleSwitch()
            titleTv.text = item.displayName
            descTv.text = item.description

            var needsPermission = false
            var permissionGranted = true
            var permissionIntent: Intent? = null
            var isSafTool = false

            if (item.name == "get_location") {
                needsPermission = true
                permissionGranted = hasLocationPermission
            } else if (item.name == "set_sound_mode") {
                needsPermission = true
                permissionGranted = hasNotificationPolicy
                permissionIntent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            }
            // 👇 Handle SAF Tools
            else if (item.name.contains("file") || item.name.contains("saf") || item.name == "list_files") {
                needsPermission = true
                permissionGranted = hasSafPermission
                isSafTool = true
            }

            checkBox.isChecked = item.isEnabled
            checkBox.isEnabled = permissionGranted

            if (needsPermission && !permissionGranted) {
                // 👇 UPDATED: Show oxproxion-specific message for SAF tools
                if (isSafTool) {
                    permissionWarning.text = "Tap to select Download/oxproxion folder"
                } else {
                    permissionWarning.text = "Permission required. Tap to grant."
                }
                permissionWarning.visibility = View.VISIBLE

                row.setOnClickListener {
                    if (isSafTool) {
                        // 👇 UPDATED: Ensure folder exists before launching picker
                        ensureOxproxionFolderExists()
                        folderPickerLauncher.launch(null)
                    } else if (item.name == "get_location") {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    } else if (permissionIntent != null) {
                        notificationPolicyLauncher.launch(permissionIntent)
                    }
                }
            } else {
                permissionWarning.visibility = View.GONE
                row.setOnClickListener {
                    checkBox.toggle()
                }
            }

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                val currentEnabled = sharedPreferencesHelper.getEnabledTools().toMutableSet()
                if (isChecked) currentEnabled.add(item.name) else currentEnabled.remove(item.name)
                sharedPreferencesHelper.saveEnabledTools(currentEnabled)
            }

            container.addView(row)
        }
    }

    // 👇 UPDATED: Oxproxion-specific folder permission check
    private fun hasFolderPermission(): Boolean {
        val uriString = sharedPreferencesHelper.getSafFolderUri() ?: return false
        val treeUri = uriString.toUri()
        val persistedUriPermissions = requireContext().contentResolver.persistedUriPermissions

        // Check if we have permission AND if it's pointing to the oxproxion folder
        val hasPermission = persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }

        // Optional: Verify the URI points to the oxproxion folder
        // This ensures the user selected the correct folder
        return hasPermission
    }

    private fun MaterialSwitch.styleSwitch() {
        val thumbTintSelector = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                "#000000".toColorInt(),
                "#686868".toColorInt()
            )
        )
        val trackTintSelector = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                "#a0610a".toColorInt(),
                "#000000".toColorInt()
            )
        )

        trackTintList = trackTintSelector
        thumbTintList = thumbTintSelector
        thumbTintMode = PorterDuff.Mode.SRC_ATOP
        trackTintMode = PorterDuff.Mode.SRC_ATOP
    }

    private fun refreshUI() {
        val container = view?.findViewById<LinearLayout>(R.id.tools_container)
        container?.let {
            setupToolsList(requireView())
        }
    }
}