package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton

class BotModelPickerFragment : Fragment() {

    var onModelSelected: ((String) -> Unit)? = null
    private lateinit var adapter: BotModelAdapter
    private val models = mutableListOf<LlmModel>()
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper

    companion object {
        const val TAG = "BotModelPickerFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_model_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chatViewModel = ViewModelProvider(requireActivity()).get(ChatViewModel::class.java)
        sharedPreferencesHelper = SharedPreferencesHelper(requireContext())

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewModels)
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val fabAddModel = view.findViewById<FloatingActionButton>(R.id.fabAddModel)

        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        loadModels()

        adapter = BotModelAdapter(models, sharedPreferencesHelper.getPreferenceModelnew(),
            onItemClicked = { selectedModel ->
                onModelSelected?.invoke(selectedModel.apiIdentifier)
                parentFragmentManager.popBackStack()
            },
            onItemEdit = { modelToEdit ->
                showEditModelDialog(modelToEdit)
            },
            onItemDelete = { modelToDelete ->
                showDeleteConfirmationDialog(modelToDelete)
            }
        )

        recyclerView.adapter = adapter

        fabAddModel.setOnClickListener {
            showAddModelDialog()
        }
    }

    private fun loadModels() {
        val builtInModels = getModelsList()
        val customModels = sharedPreferencesHelper.getCustomModels()
        models.clear()
        models.addAll(builtInModels)
        models.addAll(customModels)
        sortModels()
    }

    private fun showAddModelDialog() {
        val dialog = EditModelDialogFragment()
        dialog.onModelAdded = { model ->
            addModel(model)
        }
        dialog.show(parentFragmentManager, "add_model_dialog")
    }

    private fun showEditModelDialog(modelToEdit: LlmModel) {
        val dialog = EditModelDialogFragment()
        dialog.arguments = Bundle().apply {
            putString("displayName", modelToEdit.displayName)
            putString("apiIdentifier", modelToEdit.apiIdentifier)
            putBoolean("isVisionCapable", modelToEdit.isVisionCapable)
        }
        dialog.onModelUpdated = { oldModel, newModel ->
            updateModel(oldModel, newModel)
        }
        dialog.show(parentFragmentManager, "edit_model_dialog")
    }

    private fun showDeleteConfirmationDialog(model: LlmModel) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Model")
            .setMessage("Are you sure you want to delete '${model.displayName}'?")
            .setPositiveButton("Delete") { _, _ ->
                deleteModel(model)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addModel(model: LlmModel) {
        if (models.any { it.apiIdentifier.equals(model.apiIdentifier, ignoreCase = true) }) {
            Toast.makeText(context, "Model with this API Identifier already exists.", Toast.LENGTH_SHORT).show()
        } else {
            models.add(model)
            sortModels()
            saveCustomModels()
            adapter.notifyDataSetChanged()
            Toast.makeText(context, "Model added: ${model.displayName}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateModel(oldModel: LlmModel, newModel: LlmModel) {
        val conflictingModel = models.find { it.apiIdentifier.equals(newModel.apiIdentifier, ignoreCase = true) }
        if (conflictingModel != null && conflictingModel.apiIdentifier != oldModel.apiIdentifier) {
            Toast.makeText(context, "Another model with this API Identifier already exists.", Toast.LENGTH_SHORT).show()
            return
        }

        val index = models.indexOfFirst { it.apiIdentifier == oldModel.apiIdentifier }
        if (index != -1) {
            if (oldModel.apiIdentifier == sharedPreferencesHelper.getPreferenceModelnew()) {
                sharedPreferencesHelper.savePreferenceModelnewchat(newModel.apiIdentifier)
                chatViewModel.setModel(newModel.apiIdentifier)
            }
            models[index] = newModel
            sortModels()
            saveCustomModels()
            adapter.notifyItemChanged(index)
            adapter.notifyDataSetChanged() // To re-sort and update colors
            Toast.makeText(context, "Model updated: ${newModel.displayName}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteModel(model: LlmModel) {
        if (model.apiIdentifier == sharedPreferencesHelper.getPreferenceModelnew()) {
            val maverickModel = "meta-llama/llama-4-maverick"
            sharedPreferencesHelper.savePreferenceModelnewchat(maverickModel)
            chatViewModel.setModel(maverickModel)
            adapter.updateCurrentModel(maverickModel)
            Toast.makeText(context, "Active model changed to Maverick", Toast.LENGTH_SHORT).show()
        }
        models.remove(model)
        sortModels()
        saveCustomModels()
        adapter.notifyDataSetChanged()
        Toast.makeText(context, "Model deleted: ${model.displayName}", Toast.LENGTH_SHORT).show()
    }

    private fun saveCustomModels() {
        val builtInModelIds = getModelsList().map { it.apiIdentifier }
        val customModels = models.filter { !builtInModelIds.contains(it.apiIdentifier) }
        sharedPreferencesHelper.saveCustomModels(customModels)
    }

    private fun sortModels() {
        models.sortBy { it.displayName.lowercase() }
    }

    private fun getModelsList(): List<LlmModel> {
        return listOf(
            LlmModel("Llama 4 Maverick", "meta-llama/llama-4-maverick", true)
        )
    }
}