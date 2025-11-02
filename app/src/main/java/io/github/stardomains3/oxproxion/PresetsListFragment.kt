package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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
                    .replace(R.id.fragment_container, dialog) // replaces current fragment
                    .addToBackStack(null)
                    .commit()
            },
            onItemDelete = { preset ->
                MaterialAlertDialogBuilder(requireContext())  // CHANGED: Material for dim/consistency
                    .setMessage("Delete \"${preset.title}\"?")  // No title, as preferred
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
        adapter.update(repository.getAll().sortedBy { it.title.lowercase() })
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

        Toast.makeText(requireContext(), "Preset applied: ${preset.title}", Toast.LENGTH_SHORT).show()
        return true
    }
}