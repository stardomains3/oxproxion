package io.github.stardomains3.oxproxion

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import androidx.core.graphics.drawable.toDrawable
import androidx.appcompat.widget.SearchView  // NEW: For search functionality
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class SavedChatsFragment : Fragment() {

    private val viewModel: ChatViewModel by activityViewModels()
    private val savedChatsViewModel: SavedChatsViewModel by viewModels()
    private lateinit var savedChatsAdapter: SavedChatsAdapter
    private lateinit var searchView: SearchView  // NEW: Reference to SearchView
    private var allSessions: List<ChatSession> = emptyList()  // NEW: Store the full list for filtering

    private val exportChatsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val json = savedChatsViewModel.getChatsAsJson()
                        requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(json.toByteArray())
                        }
                        Toast.makeText(requireContext(), "Chats exported successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error exporting chats", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val importChatsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val json = requireContext().contentResolver.openInputStream(uri)?.use {
                            it.bufferedReader().readText()
                        }
                        if (json != null) {
                            savedChatsViewModel.importChatsFromJson(json)
                            Toast.makeText(requireContext(), "Chats imported successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            throw Exception("Failed to read file content.")
                        }
                    } catch (e: Exception) {
                       // Log.e("Import", "Import failed", e)
                        Toast.makeText(requireContext(), "Import failed. Check file format.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_saved_chats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.savedChatsRecyclerView)
        val toolbar = view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)

        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_import -> {
                    importChats()
                    true
                }
                R.id.action_export -> {
                    exportChats()
                    true
                }
                R.id.action_search -> {
                    // NEW: Handle search expansion (if needed)
                    true
                }
                else -> false
            }
        }


        // NEW: Setup SearchView
        val searchItem = toolbar.menu.findItem(R.id.action_search)
        if (searchItem != null) {
            searchView = searchItem.actionView as SearchView
            searchView.queryHint = "Search chats..."

            // NEW: For debouncing
            var searchJob: Job? = null

            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    // NEW: Cancel previous job and start a new one with delay
                    searchJob?.cancel()
                    searchJob = viewLifecycleOwner.lifecycleScope.launch {
                        delay(400)  // Wait 300ms
                        filterSessions(newText ?: "")
                    }
                    return true
                }
            })
        } else {
            Toast.makeText(requireContext(), "Search not available", Toast.LENGTH_SHORT).show()
        }


        savedChatsAdapter = SavedChatsAdapter(
            onClick = { session ->
                viewModel.loadChat(session.id)
                parentFragmentManager.popBackStack()
            },
            onLongClick = { session, view ->  // ← Now you get the view!
                showOptionsDialog(session, view)  // ← Pass the view to your dialog
            }
        )

        recyclerView.adapter = savedChatsAdapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // UPDATED: Observe allSessions and apply current search filter
        savedChatsViewModel.allSessions.observe(viewLifecycleOwner) { sessions ->
            allSessions = sessions ?: emptyList()  // NEW: Store full list
            filterSessions(searchView.query.toString())  // NEW: Apply current search
        }
    }

    // NEW: Function to filter sessions based on search query
    private fun filterSessions(query: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val filteredSessions = if (query.isEmpty()) {
                allSessions  // Show all sessions
            } else {
                savedChatsViewModel.searchSessions(query)  // NEW: Use optimized search
            }
            savedChatsAdapter.submitList(filteredSessions)
        }
    }

    private fun exportChats() {
        if (savedChatsAdapter.itemCount == 0) {
            Toast.makeText(requireContext(), "No chats to export.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "openchat_backup.json")
        }
        exportChatsLauncher.launch(intent)
    }

    private fun importChats() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        importChatsLauncher.launch(intent)
    }

    private fun showOptionsDialog(session: ChatSession, anchorView: View) {
        val inflater = LayoutInflater.from(requireContext())
        val menuView = inflater.inflate(R.layout.saved_popup_layout, null)

        val popupWindow = PopupWindow(
            menuView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        // Setup background and positioning
        popupWindow.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        popupWindow.isOutsideTouchable = true

        val rootView = requireActivity().window.decorView.findViewById<ViewGroup>(android.R.id.content)
        val dimView = View(requireContext()).apply {
            setBackgroundColor(Color.argb(204, 0, 0, 0))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        rootView.addView(dimView)

        popupWindow.setOnDismissListener {
            rootView.removeView(dimView)
        }

        val renameOption = menuView.findViewById<TextView>(R.id.menu_edit)
        val deleteOption = menuView.findViewById<TextView>(R.id.menu_delete)

        renameOption.setOnClickListener {
            popupWindow.dismiss()
            showRenameDialog(session)
        }

        deleteOption.setOnClickListener {
            popupWindow.dismiss()
            showDeleteConfirmationDialog(session)
        }

        // Measure the popup content to get its dimensions
        menuView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = menuView.measuredWidth
        val popupHeight = menuView.measuredHeight

        // Get the location of the anchor view on screen
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val anchorX = location[0]
        val anchorY = location[1]
        val anchorWidth = anchorView.width
        val anchorHeight = anchorView.height

        // Get screen dimensions
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Calculate available space
        val spaceBelow = screenHeight - anchorY - anchorHeight
        val spaceAbove = anchorY
        val spaceRight = screenWidth - anchorX - anchorWidth
        val spaceLeft = anchorX

        // Determine vertical position (above or below anchor)
        val showAbove = spaceBelow < popupHeight && spaceAbove >= popupHeight
        val yOffset = if (showAbove) {
            -anchorHeight - popupHeight
        } else {
            0
        }

        // Determine horizontal position (left or right of anchor)
        val showLeft = spaceRight < popupWidth && spaceLeft >= popupWidth
        val xOffset = if (showLeft) {
            -popupWidth + anchorWidth
        } else {
            0
        }

        // Show the popup with calculated offsets
        popupWindow.showAsDropDown(anchorView, xOffset, yOffset)
    }

    private fun showRenameDialog(session: ChatSession) {
        val editText = EditText(requireContext()).apply {  //, R.style.MyEditTextTheme
            setText(session.title)
            setSelection(session.title.length)
        }
        // AlertDialog.Builder(requireContext())
        // MaterialAlertDialogBuilder(requireContext(), R.style.MyCustomAlertDialogTheme)
        MaterialAlertDialogBuilder(requireContext())// R.style.CustomMaterialAlertDialogTheme)
            .setTitle("Rename Chat")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val newTitle = editText.text.toString()
                if (newTitle.isNotBlank()) {
                    savedChatsViewModel.updateSessionTitle(session.id, newTitle)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(session: ChatSession) {
        //  AlertDialog.Builder(requireContext())
        //  MaterialAlertDialogBuilder(requireContext(), R.style.MyCustomAlertDialogTheme)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Chat")
            .setMessage("Are you sure you want to delete this chat session?")
            .setPositiveButton("Delete") { _, _ ->
                savedChatsViewModel.deleteSession(session.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
