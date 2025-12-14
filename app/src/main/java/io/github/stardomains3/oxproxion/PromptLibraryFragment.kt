package io.github.stardomains3.oxproxion

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import kotlinx.serialization.json.Json
import java.util.Collections

class PromptLibraryFragment : Fragment() {

    private lateinit var promptAdapter: PromptAdapter
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private val prompts = mutableListOf<Prompt>()
    private lateinit var searchView: SearchView
    private val allPrompts = mutableListOf<Prompt>()

    private val exportPromptsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val promptsList = sharedPreferencesHelper.getCustomPrompts()
                        val json = Json.encodeToString(promptsList)
                        requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(json.toByteArray())
                        }
                        Toast.makeText(requireContext(), "Prompts exported successfully", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Error exporting prompts", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val importPromptsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val jsonString = requireContext().contentResolver.openInputStream(uri)?.use {
                            it.bufferedReader().readText()
                        }
                        if (jsonString != null) {
                            val importedPrompts = Json.decodeFromString<List<Prompt>>(jsonString)
                            val currentPrompts = sharedPreferencesHelper.getCustomPrompts().toMutableList()

                            importedPrompts.forEach { importedPrompt ->
                                val isDuplicate = currentPrompts.any { it.title == importedPrompt.title }
                                if (!isDuplicate) {
                                    currentPrompts.add(importedPrompt)
                                }
                            }
                            sharedPreferencesHelper.saveCustomPrompts(currentPrompts)
                            loadPrompts()
                            Toast.makeText(requireContext(), "Prompts imported successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            throw Exception("Failed to read file content.")
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Import failed. Check file format.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_prompt_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferencesHelper = SharedPreferencesHelper(requireContext())

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        val searchItem = toolbar.menu.findItem(R.id.action_search)
        searchView = searchItem.actionView as SearchView
        searchView.queryHint = "Search prompts..."
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                filterPrompts(newText ?: "")
                return true
            }
        })

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_import -> {
                    importPrompts()
                    true
                }
                R.id.action_export -> {
                    exportPrompts()
                    true
                }
                else -> false
            }
        }

        setupRecyclerView(view)
        loadPrompts()

        view.findViewById<FloatingActionButton>(R.id.fab_add_prompt).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AddEditPromptFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun exportPrompts() {
        if (sharedPreferencesHelper.getCustomPrompts().isEmpty()) {
            Toast.makeText(requireContext(), "No prompts to export.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_TITLE, "prompts.json")
        }
        exportPromptsLauncher.launch(intent)
    }

    private fun importPrompts() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        importPromptsLauncher.launch(intent)
    }

    private fun setupRecyclerView(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.prompt_recycler_view)
        promptAdapter = PromptAdapter(prompts, onItemClick = { prompt ->
            // Copy to clipboard
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Prompt", prompt.prompt)
            clipboard.setPrimaryClip(clip)
            recyclerView.postDelayed({
                parentFragmentManager.popBackStack()  // Pop PromptLibraryFragment
                parentFragmentManager.popBackStack()  // Pop SettingsFragment → back to ChatFragment
            }, 200)
            Toast.makeText(requireContext(), "Copied to clipboard: ${prompt.title}", Toast.LENGTH_SHORT).show()

        }, onMenuClick = { anchorView, prompt ->
            showPopupMenu(anchorView, prompt)
        })

        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = promptAdapter

            // Drag-and-drop reordering (all items)
            val callback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
                override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                    val fromPos = viewHolder.bindingAdapterPosition
                    val toPos = target.bindingAdapterPosition
                    Collections.swap(prompts, fromPos, toPos)
                    promptAdapter.notifyItemMoved(fromPos, toPos)
                    sharedPreferencesHelper.saveCustomPrompts(prompts)
                    return true
                }
                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
                override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
                    ItemTouchHelper.Callback.makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
            }
            ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
        }
    }

    private fun showPopupMenu(anchorView: View, prompt: Prompt) {
        val inflater = LayoutInflater.from(anchorView.context)
        val menuView = inflater.inflate(R.layout.menu_popup_layout, null)
        val popupWindow = PopupWindow(menuView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popupWindow.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        popupWindow.isOutsideTouchable = true

        // Dim overlay (same as system)
        val rootView = requireActivity().window.decorView.findViewById<ViewGroup>(android.R.id.content)
        val dimView = View(requireContext()).apply {
            setBackgroundColor(Color.argb(204, 0, 0, 0))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        rootView.addView(dimView)
        popupWindow.setOnDismissListener { rootView.removeView(dimView) }

        val editItem = menuView.findViewById<TextView>(R.id.menu_edit)
        val deleteItem = menuView.findViewById<TextView>(R.id.menu_delete)

        editItem.setOnClickListener {
            popupWindow.dismiss()
            navigateToEditScreen(prompt)
        }

        deleteItem.setOnClickListener {
            popupWindow.dismiss()
            showDeleteConfirmationDialog(prompt)
        }

        // Smart positioning (same as system)
        menuView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupHeight = menuView.measuredHeight
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val anchorY = location[1]
        val anchorHeight = anchorView.height
        val windowMetrics = requireActivity().windowManager.currentWindowMetrics
        val screenHeight = windowMetrics.bounds.height()
        val spaceBelow = screenHeight - anchorY - anchorHeight
        val spaceAbove = anchorY
        val showAbove = spaceBelow < popupHeight && spaceAbove >= popupHeight

        if (showAbove) {
            popupWindow.showAsDropDown(anchorView, 0, -anchorHeight - popupHeight)
        } else {
            popupWindow.showAsDropDown(anchorView)
        }
    }

    private fun navigateToEditScreen(prompt: Prompt) {
        val fragment = AddEditPromptFragment().apply {
            arguments = Bundle().apply {
                putString("ARG_TITLE", prompt.title)
                putString("ARG_PROMPT", prompt.prompt)
            }
        }
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun loadPrompts() {
        prompts.clear()
        allPrompts.clear()
        val customPrompts = sharedPreferencesHelper.getCustomPrompts()

        customPrompts.forEach { it.isExpanded = false }

        prompts.addAll(customPrompts)
        allPrompts.addAll(customPrompts)

        if (::searchView.isInitialized) {
            filterPrompts(searchView.query.toString())
        } else {
            promptAdapter.notifyDataSetChanged()
        }
    }

    private fun filterPrompts(query: String) {
        if (allPrompts.isNotEmpty()) {
            val filtered = if (query.isEmpty()) allPrompts else allPrompts.filter {
                it.title.contains(query, ignoreCase = true) || it.prompt.contains(query, ignoreCase = true)
            }
            prompts.clear()
            prompts.addAll(filtered)
            prompts.forEach { it.isExpanded = false }
        }
        promptAdapter.notifyDataSetChanged()
    }

    private fun showDeleteConfirmationDialog(prompt: Prompt) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Prompt")
            .setMessage("Are you sure you want to delete this prompt?")
            .setPositiveButton("Delete") { _, _ -> deletePrompt(prompt) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePrompt(prompt: Prompt) {
        val customPrompts = sharedPreferencesHelper.getCustomPrompts().toMutableList()
        if (customPrompts.remove(prompt)) {
            sharedPreferencesHelper.saveCustomPrompts(customPrompts)
            loadPrompts()
        }
    }

    override fun onResume() {
        super.onResume()
        loadPrompts()
    }
}