package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton

class BotModelPickerFragment : Fragment() {

    var onModelSelected: ((String) -> Unit)? = null
    private lateinit var adapter: BotModelAdapter
    private var models = mutableListOf<LlmModel>()
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private lateinit var searchView: SearchView
    private var filteredModels: MutableList<LlmModel> = mutableListOf()
    // Filter bar references
    private lateinit var sortBar: MaterialButtonToggleGroup
    private lateinit var filterBar: MaterialButtonToggleGroup
    private lateinit var costFilterBar: MaterialButtonToggleGroup
    private lateinit var sourceFilterBar: MaterialButtonToggleGroup  // NEW: Fourth filter bar
    // Filter and sort state
    private var currentFilterType: FilterType = FilterType.ALL
    private var currentCostFilter: CostFilter = CostFilter.ALL
    private var currentSourceFilter: SourceFilter = SourceFilter.ALL  // NEW: Track source filter
    private var currentSortOrder: SortOrder = SortOrder.ALPHABETICAL

    // Enums for filters
    enum class FilterType {
        ALL, VISION, IMAGE_GEN
    }

    // UPDATED: Removed LAN from CostFilter
    enum class CostFilter {
        ALL, FREE, PAID
    }

    // NEW: Enum for source filtering (LAN vs Cloud)
    enum class SourceFilter {
        ALL, LAN, CLOUD
    }

    enum class SortOrder {
        ALPHABETICAL, BY_DATE
    }

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

        chatViewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
        sharedPreferencesHelper = SharedPreferencesHelper(requireContext())

        chatViewModel.customModelsUpdated.observe(viewLifecycleOwner, Observer { event ->
            event.getContentIfNotHandled()?.let {
                loadModels()
                if (::adapter.isInitialized) {
                    adapter.notifyDataSetChanged()
                }
            }
        })

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewModels)
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val fabAddModel = view.findViewById<FloatingActionButton>(R.id.fabAddModel)
        // Initialize toggle groups
        sortBar = view.findViewById(R.id.sortBar)
        filterBar = view.findViewById(R.id.filterBar)
        costFilterBar = view.findViewById(R.id.costFilterBar)
        sourceFilterBar = view.findViewById(R.id.sourceFilterBar)  // NEW: Initialize fourth bar

        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_lan_models -> {
                    parentFragmentManager.beginTransaction()
                        .replace(android.R.id.content, LanModelsFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                R.id.action_open_router_models -> {
                    parentFragmentManager.beginTransaction()
                        .replace(android.R.id.content, OpenRouterModelsFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                R.id.action_search -> {
                    true
                }
                else -> false
            }
        }

        // Initialize SearchView from the menu
        val searchItem = toolbar.menu.findItem(R.id.action_search)
        if (searchItem != null) {
            searchView = searchItem.actionView as SearchView
            searchView.queryHint = "Search models..."
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    filterAndSortModels(newText ?: "")
                    return true
                }
            })
        } else {
            Toast.makeText(requireContext(), "Search not available", Toast.LENGTH_SHORT).show()
        }

        currentSortOrder = sharedPreferencesHelper.getBotModelPickerSortOrder()
        updateSortButtons(currentSortOrder)
        sortBar.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                currentSortOrder = when (checkedId) {
                    R.id.sortAlphabeticalButton -> SortOrder.ALPHABETICAL
                    R.id.sortDateButton -> SortOrder.BY_DATE
                    else -> SortOrder.ALPHABETICAL
                }
                sharedPreferencesHelper.saveBotModelPickerSortOrder(currentSortOrder)
                filterAndSortModels(searchView.query.toString())
            }
        }

        // Set up type filter bar
        filterBar.check(R.id.filterAllButton)
        filterBar.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                currentFilterType = when (checkedId) {
                    R.id.filterAllButton -> FilterType.ALL
                    R.id.filterVisionButton -> FilterType.VISION
                    R.id.filterImageGenButton -> FilterType.IMAGE_GEN
                    else -> FilterType.ALL
                }
                filterAndSortModels(searchView.query.toString())
            }
        }

        // Set up cost filter bar (UPDATED: Removed LAN handling)
        costFilterBar.check(R.id.costFilterAllButton)
        costFilterBar.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                currentCostFilter = when (checkedId) {
                    R.id.costFilterAllButton -> CostFilter.ALL
                    R.id.costFilterFreeButton -> CostFilter.FREE
                    R.id.costFilterPaidButton -> CostFilter.PAID
                    else -> CostFilter.ALL
                }
                filterAndSortModels(searchView.query.toString())
            }
        }

        // NEW: Set up source filter bar (fourth bar)
        sourceFilterBar.check(R.id.sourceFilterAllButton)
        sourceFilterBar.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                currentSourceFilter = when (checkedId) {
                    R.id.sourceFilterAllButton -> SourceFilter.ALL
                    R.id.sourceFilterLanButton -> SourceFilter.LAN
                    R.id.sourceFilterCloudButton -> SourceFilter.CLOUD
                    else -> SourceFilter.ALL
                }
                filterAndSortModels(searchView.query.toString())
            }
        }

        adapter = BotModelAdapter(filteredModels, sharedPreferencesHelper.getPreferenceModelnew(),
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
        loadModels()

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
        filterAndSortModels(searchView.query.toString())
    }

    // UPDATED: Combined function for filtering and sorting with new cost/source logic
    private fun filterAndSortModels(query: String) {
        var tempFiltered = models.toMutableList()

        // Apply type filter
        tempFiltered = when (currentFilterType) {
            FilterType.ALL -> tempFiltered
            FilterType.VISION -> tempFiltered.filter { it.isVisionCapable }.toMutableList()
            FilterType.IMAGE_GEN -> tempFiltered.filter { it.isImageGenerationCapable }.toMutableList()
        }

        // Apply cost filter (UPDATED: New logic)
        // FREE = LAN models (free) + free OpenRouter models
        // PAID = paid OpenRouter models only
        tempFiltered = when (currentCostFilter) {
            CostFilter.ALL -> tempFiltered
            CostFilter.FREE -> tempFiltered.filter {
                it.isLANModel || (!it.isLANModel && it.isFree)
            }.toMutableList()
            CostFilter.PAID -> tempFiltered.filter {
                !it.isLANModel && !it.isFree
            }.toMutableList()
        }

        // NEW: Apply source filter (LAN vs Cloud/OpenRouter)
        tempFiltered = when (currentSourceFilter) {
            SourceFilter.ALL -> tempFiltered
            SourceFilter.LAN -> tempFiltered.filter { it.isLANModel }.toMutableList()
            SourceFilter.CLOUD -> tempFiltered.filter { !it.isLANModel }.toMutableList()
        }

        // Apply search filter
        tempFiltered = if (query.isEmpty()) {
            tempFiltered
        } else {
            tempFiltered.filter { model ->
                model.displayName.contains(query, ignoreCase = true) ||
                        model.apiIdentifier.contains(query, ignoreCase = true)
            }.toMutableList()
        }

        // Apply sorting
        when (currentSortOrder) {
            SortOrder.ALPHABETICAL -> tempFiltered.sortBy { it.displayName.lowercase() }
            SortOrder.BY_DATE -> tempFiltered.sortByDescending { it.created }
        }

        filteredModels = tempFiltered
        adapter.updateModels(filteredModels)
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
            putBoolean("isReasoningCapable", modelToEdit.isReasoningCapable)
            putBoolean("isImageGenerationCapable", modelToEdit.isImageGenerationCapable)
            putLong("created", modelToEdit.created)
            putBoolean("isLANModel", modelToEdit.isLANModel)
            putBoolean("isFree", modelToEdit.isFree)
        }
        dialog.onModelUpdated = { oldModel, newModel ->
            updateModel(oldModel, newModel)
        }
        dialog.show(parentFragmentManager, "edit_model_dialog")
    }

    private fun showDeleteConfirmationDialog(model: LlmModel) {
        MaterialAlertDialogBuilder(requireContext())
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
            saveCustomModels()
            filterAndSortModels(searchView.query.toString())
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
            saveCustomModels()
            filterAndSortModels(searchView.query.toString())
            adapter.notifyDataSetChanged()
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
        saveCustomModels()
        filterAndSortModels(searchView.query.toString())
        adapter.notifyDataSetChanged()
        Toast.makeText(context, "Model deleted: ${model.displayName}", Toast.LENGTH_SHORT).show()
    }

    private fun saveCustomModels() {
        val builtInModelIds = getModelsList().map { it.apiIdentifier }
        val customModels = models.filter { !builtInModelIds.contains(it.apiIdentifier) }
        sharedPreferencesHelper.saveCustomModels(customModels)
    }

    private fun getModelsList(): List<LlmModel> {
        return listOf(
            LlmModel("Meta: Llama 4 Maverick", "meta-llama/llama-4-maverick", true)
        )
    }

    private fun updateSortButtons(sortOrder: SortOrder) {
        when (sortOrder) {
            SortOrder.ALPHABETICAL -> {
                sortBar.check(R.id.sortAlphabeticalButton)
            }
            SortOrder.BY_DATE -> {
                sortBar.check(R.id.sortDateButton)
            }
        }
    }
}
