package io.github.stardomains3.oxproxion

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.Collections

class SystemMessageLibraryFragment : Fragment() {

    private lateinit var systemMessageAdapter: SystemMessageAdapter
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private val systemMessages = mutableListOf<SystemMessage>()
    private lateinit var searchView: SearchView  // NEW: Reference to SearchView
    private val allSystemMessages = mutableListOf<SystemMessage>()  // NEW: Store full list for filtering

    private val exportSystemMessagesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val customMessages = sharedPreferencesHelper.getCustomSystemMessages()
                        val defaultMessage = sharedPreferencesHelper.getDefaultSystemMessage()

                        // Create a new list with default message first, then custom messages
                        val allMessages = mutableListOf<SystemMessage>().apply {
                            // Add default message without the isDefault property
                            add(SystemMessage(defaultMessage.title, defaultMessage.prompt))
                            addAll(customMessages)
                        }

                        val json = Json.encodeToString(allMessages)
                        requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(json.toByteArray())
                        }
                        Toast.makeText(requireContext(), "System Messages exported successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error exporting System Messages", Toast.LENGTH_SHORT).show()
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
                            // Fetch the single default message for duplicate checking
                            val defaultMessage = sharedPreferencesHelper.getDefaultSystemMessage()

                            importedMessages.forEach { importedMessage ->
                                // Check for duplicates against current custom messages AND the single default message
                                val isDuplicateInCustoms = currentMessages.any { it.title == importedMessage.title }
                                val isDuplicateInDefault = (importedMessage.title == defaultMessage.title)

                                if (!isDuplicateInCustoms && !isDuplicateInDefault) {
                                    currentMessages.add(importedMessage)
                                }
                                // Optional: Log skipped duplicates
                                // else { Log.d("Import", "Skipped duplicate: ${importedMessage.title}") }
                            }
                            sharedPreferencesHelper.saveCustomSystemMessages(currentMessages)
                            loadSystemMessages()
                            Toast.makeText(requireContext(), "System Messages imported successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            throw Exception("Failed to read file content.")
                        }
                    } catch (e: SerializationException) {
                        // Log.e("Import", "Import failed due to JSON format", e)
                        Toast.makeText(requireContext(), "Import failed. Check file format.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        //Log.e("Import", "Import failed", e)
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
        val searchItem = toolbar.menu.findItem(R.id.action_search)
        if (searchItem != null) {
            searchView = searchItem.actionView as SearchView
            searchView.queryHint = "Search system messages..."
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    filterSystemMessages(newText ?: "")  // NEW: Filter on every keystroke (no debounce)
                    return true
                }
            })
        } else {
            Toast.makeText(requireContext(), "Search not available", Toast.LENGTH_SHORT).show()
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
        val recyclerView = view.findViewById<RecyclerView>(R.id.system_message_recycler_view)
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = systemMessageAdapter

            // Drag-and-drop reordering (customs only; default fixed at top)
            val callback = object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,  // Vertical drag only
                0  // No swipe
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val fromPos = viewHolder.bindingAdapterPosition  // Updated: Use bindingAdapterPosition instead of deprecated adapterPosition
                    val toPos = target.bindingAdapterPosition  // Updated: Use bindingAdapterPosition instead of deprecated adapterPosition
                    // Only allow reordering if both are custom (index > 0)
                    if (fromPos > 0 && toPos > 0) {
                        Collections.swap(systemMessages, fromPos, toPos)
                        systemMessageAdapter.notifyItemMoved(fromPos, toPos)
                        // Persist: Save reordered customs (skip default)
                        val customs = systemMessages.drop(1).toMutableList()
                        sharedPreferencesHelper.saveCustomSystemMessages(customs)
                        return true
                    }
                    return false
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    // No swipe
                }

                override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                    // No drag on default (position 0)
                    return if (viewHolder.bindingAdapterPosition > 0) {  // Updated: Use bindingAdapterPosition instead of deprecated adapterPosition
                        makeMovementFlags(
                            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                            0
                        )
                    } else {
                        0
                    }
                }
            }
            val itemTouchHelper = ItemTouchHelper(callback)
            itemTouchHelper.attachToRecyclerView(recyclerView)
        }
    }

    private fun showPopupMenu(anchorView: View, systemMessage: SystemMessage) {
        val inflater = LayoutInflater.from(anchorView.context)
        val menuView = inflater.inflate(R.layout.menu_popup_layout, null)

        val popupWindow = PopupWindow(
            menuView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // Setup background - Important for dismissing when touching outside
        popupWindow.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        popupWindow.isOutsideTouchable = true
        val rootView = requireActivity().window.decorView.findViewById<ViewGroup>(android.R.id.content)
        val dimView = View(requireContext()).apply {
            setBackgroundColor(Color.argb(204, 0, 0, 0)) // 150 = ~60% opacity
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        rootView.addView(dimView)
        // Remove dim when popup is dismissed
        popupWindow.setOnDismissListener {
            rootView.removeView(dimView)
        }
        val editItem = menuView.findViewById<TextView>(R.id.menu_edit)
        val deleteItem = menuView.findViewById<TextView>(R.id.menu_delete)

        // Enable edit option for default messages, disable delete
        if (systemMessage.isDefault) {
            deleteItem.visibility = View.GONE
        }

        editItem.setOnClickListener {
            popupWindow.dismiss()
            navigateToEditScreen(systemMessage)
        }

        deleteItem.setOnClickListener {
            popupWindow.dismiss()
            if (systemMessage.isDefault) {
                Toast.makeText(context, "Default system message cannot be deleted", Toast.LENGTH_SHORT).show()
            } else {
                showDeleteConfirmationDialog(systemMessage)
            }
        }

        // --- SMART POSITIONING LOGIC ---

        // Measure the popup content to get its height
        menuView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupHeight = menuView.measuredHeight

        // Get the location of the anchor view on screen
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val anchorY = location[1]
        val anchorHeight = anchorView.height

        // Get screen height using WindowMetrics (replaces deprecated DisplayMetrics)
        val windowMetrics = (context as Activity).windowManager.currentWindowMetrics
        val screenHeight = windowMetrics.bounds.height()

        // Calculate available space below and above the anchor
        val spaceBelow = screenHeight - anchorY - anchorHeight
        val spaceAbove = anchorY

        // Decide whether to show above or below based on available space
        val showAbove = spaceBelow < popupHeight && spaceAbove >= popupHeight

        // Show the popup in the correct position
        if (showAbove) {
            // Show above the anchor view
            popupWindow.showAsDropDown(anchorView, 0, -anchorHeight - popupHeight)
        } else {
            // Show below the anchor view (default behavior)
            popupWindow.showAsDropDown(anchorView)
        }
    }


    /*private fun showPopupMenu(view: View, systemMessage: SystemMessage) {
        val popup = PopupMenu(context, view)
        popup.menuInflater.inflate(R.menu.model_item_menu, popup.menu)

        // Enable edit option for default messages, disable delete
        if (systemMessage.isDefault) {
            popup.menu.findItem(R.id.delete_model).isVisible = false
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.edit_model -> {
                    navigateToEditScreen(systemMessage)
                    true
                }
                R.id.delete_model -> {
                    if (systemMessage.isDefault) {
                        Toast.makeText(context, "Default system message cannot be deleted", Toast.LENGTH_SHORT).show()
                    } else {
                        showDeleteConfirmationDialog(systemMessage)
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }*/

    private fun navigateToEditScreen(systemMessage: SystemMessage) {
        val fragment = AddEditSystemMessageFragment().apply {
            arguments = Bundle().apply {
                putString(AddEditSystemMessageFragment.ARG_TITLE, systemMessage.title)
                putString(AddEditSystemMessageFragment.ARG_PROMPT, systemMessage.prompt)
                putBoolean(AddEditSystemMessageFragment.ARG_IS_DEFAULT, systemMessage.isDefault)
            }
        }
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }
    override fun onResume() {
        super.onResume()
        if (::systemMessageAdapter.isInitialized) {
            systemMessageAdapter.selectedMessage = sharedPreferencesHelper.getSelectedSystemMessage()
            loadSystemMessages()
        }
    }
    private fun loadSystemMessages() {
        systemMessages.clear()
        allSystemMessages.clear()  // NEW: Clear the full list too
        val defaultMessage = sharedPreferencesHelper.getDefaultSystemMessage()
        val customMessages = sharedPreferencesHelper.getCustomSystemMessages()

        // Reset expand states (compact by default)
        defaultMessage.isExpanded = false
        customMessages.forEach { it.isExpanded = false }

        systemMessages.add(defaultMessage)
        systemMessages.addAll(customMessages)

        allSystemMessages.add(defaultMessage)  // NEW: Populate the full list
        allSystemMessages.addAll(customMessages)

        // Apply search filter only if searchView is initialized
        if (::searchView.isInitialized) {
            filterSystemMessages(searchView.query.toString())
        } else {
            systemMessageAdapter.notifyDataSetChanged()  // Fallback for non-search calls
        }
    }


    private fun filterSystemMessages(query: String) {
        if (allSystemMessages.isNotEmpty()) {
            // Use the full list for filtering
            val filteredMessages = if (query.isEmpty()) {
                allSystemMessages
            } else {
                allSystemMessages.filter { message ->
                    message.title.contains(query, ignoreCase = true) ||
                            message.prompt.contains(query, ignoreCase = true)
                }
            }
            systemMessages.clear()
            systemMessages.addAll(filteredMessages)
            // Collapse all filtered items
            systemMessages.forEach { it.isExpanded = false }
        }
        systemMessageAdapter.notifyDataSetChanged()
    }




    private fun showDeleteConfirmationDialog(systemMessage: SystemMessage) {
        // MaterialAlertDialogBuilder(requireContext(), R.style.MyCustomAlertDialogTheme)
        MaterialAlertDialogBuilder(requireContext())
            // AlertDialog.Builder(requireContext())
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
                // Use the saved default message instead of creating a new instance
                val defaultMessage = sharedPreferencesHelper.getDefaultSystemMessage()
                sharedPreferencesHelper.saveSelectedSystemMessage(defaultMessage)
                systemMessageAdapter.selectedMessage = defaultMessage
            }
            loadSystemMessages()
        }
    }

}