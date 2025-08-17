package io.github.stardomains3.oxproxion

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SystemMessageLibraryFragment : Fragment() {

    private lateinit var systemMessageAdapter: SystemMessageAdapter
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private val systemMessages = mutableListOf<SystemMessage>()

    private val exportSystemMessagesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val customMessages = sharedPreferencesHelper.getCustomSystemMessages()
                        val json = Json.encodeToString(customMessages)
                        requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(json.toByteArray())
                        }
                        Toast.makeText(requireContext(), "System messages exported successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error exporting system messages", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val importSystemMessagesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val jsonString = requireContext().contentResolver.openInputStream(uri)?.use {
                            it.bufferedReader().readText()
                        }
                        if (jsonString != null) {
                            val importedMessages = Json.decodeFromString<List<SystemMessage>>(jsonString)
                            val currentMessages = sharedPreferencesHelper.getCustomSystemMessages().toMutableList()
                            importedMessages.forEach { importedMessage ->
                                if (!currentMessages.any { it.title == importedMessage.title }) {
                                    currentMessages.add(importedMessage)
                                }
                            }
                            sharedPreferencesHelper.saveCustomSystemMessages(currentMessages)
                            loadSystemMessages()
                            Toast.makeText(requireContext(), "System messages imported successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            throw Exception("Failed to read file content.")
                        }
                    } catch (e: SerializationException) {
                        Log.e("Import", "Import failed due to JSON format", e)
                        Toast.makeText(requireContext(), "Import failed. Check file format.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("Import", "Import failed", e)
                        Toast.makeText(requireContext(), "Import failed.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_system_message_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferencesHelper = SharedPreferencesHelper(requireContext())

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_import -> {
                    importSystemMessages()
                    true
                }
                R.id.action_export -> {
                    exportSystemMessages()
                    true
                }
                else -> false
            }
        }

        setupRecyclerView(view)
        loadSystemMessages()

        view.findViewById<FloatingActionButton>(R.id.fab_add_system_message).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AddEditSystemMessageFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun exportSystemMessages() {
        if (sharedPreferencesHelper.getCustomSystemMessages().isEmpty()) {
            Toast.makeText(requireContext(), "No system messages to export.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "system_messages.json")
        }
        exportSystemMessagesLauncher.launch(intent)
    }

    private fun importSystemMessages() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        importSystemMessagesLauncher.launch(intent)
    }

    private fun setupRecyclerView(view: View) {
        val selectedMessage = sharedPreferencesHelper.getSelectedSystemMessage()
        systemMessageAdapter = SystemMessageAdapter(systemMessages, selectedMessage,
            onItemClick = { systemMessage ->
                sharedPreferencesHelper.saveSelectedSystemMessage(systemMessage)
                view.postDelayed({
                    parentFragmentManager.popBackStack()
                }, 200)
            },
            onMenuClick = { anchorView, systemMessage ->
                showPopupMenu(anchorView, systemMessage)
            }
        )
        view.findViewById<RecyclerView>(R.id.system_message_recycler_view).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = systemMessageAdapter
        }
    }

    private fun showPopupMenu(view: View, systemMessage: SystemMessage) {
        val popup = PopupMenu(context, view)
        popup.menuInflater.inflate(R.menu.model_item_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.edit_model -> {
                    navigateToEditScreen(systemMessage)
                    true
                }
                R.id.delete_model -> {
                    showDeleteConfirmationDialog(systemMessage)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun navigateToEditScreen(systemMessage: SystemMessage) {
        val fragment = AddEditSystemMessageFragment().apply {
            arguments = Bundle().apply {
                putString(AddEditSystemMessageFragment.ARG_TITLE, systemMessage.title)
                putString(AddEditSystemMessageFragment.ARG_PROMPT, systemMessage.prompt)
            }
        }
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun loadSystemMessages() {
        systemMessages.clear()
        systemMessages.add(SystemMessage("Default", "You are a helpful assistant.", isDefault = true))
        systemMessages.addAll(sharedPreferencesHelper.getCustomSystemMessages())
        systemMessageAdapter.notifyDataSetChanged()
    }

    private fun showDeleteConfirmationDialog(systemMessage: SystemMessage) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete System Message")
            .setMessage("Are you sure you want to delete this system message?")
            .setPositiveButton("Delete") { _, _ ->
                deleteSystemMessage(systemMessage)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSystemMessage(systemMessage: SystemMessage) {
        val customMessages = sharedPreferencesHelper.getCustomSystemMessages().toMutableList()
        if (customMessages.remove(systemMessage)) {
            sharedPreferencesHelper.saveCustomSystemMessages(customMessages)
            if (sharedPreferencesHelper.getSelectedSystemMessage() == systemMessage) {
                val defaultMessage = SystemMessage("Default", "You are a helpful assistant.", isDefault = true)
                sharedPreferencesHelper.saveSelectedSystemMessage(defaultMessage)
                systemMessageAdapter.selectedMessage = defaultMessage
            }
            loadSystemMessages()
        }
    }
}

