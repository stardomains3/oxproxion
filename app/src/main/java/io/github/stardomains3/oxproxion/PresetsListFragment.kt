package io.github.stardomains3.oxproxion


import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch


class PresetsListFragment : Fragment() {

    private lateinit var viewModel: ChatViewModel
    private lateinit var repository: PresetRepository
    private lateinit var adapter: PresetAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_presets_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerViewPresets)
        val fab = view.findViewById<FloatingActionButton>(R.id.fabAddPreset)
        setupClearChatSwitch()
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        viewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
        repository = PresetRepository(requireContext())

        adapter = PresetAdapter(
            onItemClicked = { preset ->
                if (applyPresetSafely(preset)) {
                    parentFragmentManager.popBackStack()
                }
            },
            onItemEdit = { preset ->
                val dialog = PresetEditFragment.newInstance(preset)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, dialog)
                    .addToBackStack(null)
                    .commit()
            },
            onItemDelete = { preset ->
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage("Delete \"${preset.title}\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        repository.deleteById(preset.id)
                        refresh()
                        Toast.makeText(requireContext(), "Preset deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        // Add drag and drop functionality
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false

                adapter.move(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // No-op
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                // Persist the new order when drag is released
                repository.saveAll(adapter.getItems())
            }
        }
        val itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recycler)

        fab.setOnClickListener {
            val dialog = PresetEditFragment.newInstance(null)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, dialog)
                .addToBackStack(null)
                .commit()
        }

        refresh()
    }

    private fun refresh() {
        // Load presets in saved order (no sorting)
        adapter.update(repository.getAll())
    }
    private fun setupClearChatSwitch() {
        val switch = view?.findViewById<MaterialSwitch>(R.id.switchClearChat)  // Updated type
        switch?.apply {
            // Optional: Custom tints if XML doesn't match your theme (uncomment if needed)
            // thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.white))
            // trackTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
            val thumbTintSelector = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    "#000000".toColorInt(),  // Checked state color
                    "#686868".toColorInt()   // Unchecked state color
                )
            )
            val trackTintSelector = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    "#a0610a".toColorInt(),  // On state color
                    "#000000".toColorInt()   // Off state color
                )
            )
            switch.trackTintList = trackTintSelector
            switch.thumbTintList = thumbTintSelector
            switch.thumbTintMode = PorterDuff.Mode.SRC_ATOP
            switch.trackTintMode = PorterDuff.Mode.SRC_ATOP
            // Restore state from SharedPreferences
            val sharedPrefs = SharedPreferencesHelper(requireContext())
            isChecked = sharedPrefs.getClearChatDefault2()

            // One-tap toggle: Saves state immediately
            setOnCheckedChangeListener { _, isChecked ->
                sharedPrefs.saveClearChatDefault2(isChecked)
                // Optional: Toast feedback
                // Toast.makeText(requireContext(), if (isChecked) "Will clear chat" else "Won't clear", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun applyPresetSafely(preset: Preset): Boolean {
        val allModels = (viewModel.getBuiltInModels() + SharedPreferencesHelper(requireContext()).getCustomModels()).distinctBy { it.apiIdentifier.lowercase() }
        val selectedModel = allModels.find { it.apiIdentifier.equals(preset.modelIdentifier, ignoreCase = true) }
        if (selectedModel == null) {
            Toast.makeText(requireContext(), "Preset not applied: Model \"${preset.modelIdentifier}\" no longer exists.", Toast.LENGTH_LONG).show()
            return false
        }

        val allMessages = listOf(SharedPreferencesHelper(requireContext()).getDefaultSystemMessage()) + SharedPreferencesHelper(requireContext()).getCustomSystemMessages()
        val existsMessage = allMessages.any { it.title == preset.systemMessage.title && it.prompt == preset.systemMessage.prompt }
        if (!existsMessage) {
            Toast.makeText(requireContext(), "Preset not applied: Saved system message not found.", Toast.LENGTH_LONG).show()
            return false
        }

        PresetManager.applyPreset(requireContext(), viewModel, preset)
        val sharedPrefs = SharedPreferencesHelper(requireContext())
        if (sharedPrefs.getClearChatDefault2()) {
            viewModel.startNewChat()
        }
        if (ForegroundService.isRunningForeground && SharedPreferencesHelper(requireContext()).getNotiPreference()) {
            val displayName = viewModel.getModelDisplayName(preset.title)
            ForegroundService.updateNotificationStatusSilently(displayName, "Preset Applied")
        }

        Toast.makeText(requireContext(), "Preset applied: ${preset.title}", Toast.LENGTH_SHORT).show()
        return true
    }
}