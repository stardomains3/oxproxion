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

    private lateinit var sortBar: MaterialButtonToggleGroup
    private lateinit var filterBar: MaterialButtonToggleGroup
    private lateinit var costFilterBar: MaterialButtonToggleGroup
    private lateinit var sourceFilterBar: MaterialButtonToggleGroup

    private var currentFilterType: FilterType = FilterType.ALL
    private var currentCostFilter: CostFilter = CostFilter.ALL
    private var currentSourceFilter: SourceFilter = SourceFilter.ALL
    private var currentSortOrder: SortOrder = SortOrder.ALPHABETICAL

    enum class FilterType { ALL, VISION, IMAGE_GEN }
    enum class CostFilter { ALL, FREE, PAID }
    enum class SourceFilter { ALL, LAN, CLOUD }
    enum class SortOrder { ALPHABETICAL, BY_DATE }

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

        // Initialize Views
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewModels)
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val fabAddModel = view.findViewById<FloatingActionButton>(R.id.fabAddModel)
        sortBar = view.findViewById(R.id.sortBar)
        filterBar = view.findViewById(R.id.filterBar)
        costFilterBar = view.findViewById(R.id.costFilterBar)
        sourceFilterBar = view.findViewById(R.id.sourceFilterBar)

        // --- LOAD SAVED STATES ---
        currentSortOrder = sharedPreferencesHelper.getBotModelPickerSortOrder()

        currentFilterType = when (sharedPreferencesHelper.getBotPickerFilterType()) {
            "VISION" -> FilterType.VISION
            "IMAGE_GEN" -> FilterType.IMAGE_GEN
            else -> FilterType.ALL
        }

        currentCostFilter = when (sharedPreferencesHelper.getBotPickerCostFilter()) {
            "FREE" -> CostFilter.FREE
            "PAID" -> CostFilter.PAID
            else -> CostFilter.ALL
        }

        currentSourceFilter = when (sharedPreferencesHelper.getBotPickerSourceFilter()) {
            "LAN" -> SourceFilter.LAN
            "CLOUD" -> SourceFilter.CLOUD
            else -> SourceFilter.ALL
        }

        // --- APPLY VISUAL CHECKS ---
        updateSortButtons(currentSortOrder)

        filterBar.check(when (currentFilterType) {
            FilterType.ALL -> R.id.filterAllButton
            FilterType.VISION -> R.id.filterVisionButton
            FilterType.IMAGE_GEN -> R.id.filterImageGenButton
        })

        costFilterBar.check(when (currentCostFilter) {
            CostFilter.ALL -> R.id.costFilterAllButton
            CostFilter.FREE -> R.id.costFilterFreeButton
            CostFilter.PAID -> R.id.costFilterPaidButton
        })

        sourceFilterBar.check(when (currentSourceFilter) {
            SourceFilter.ALL -> R.id.sourceFilterAllButton
            SourceFilter.LAN -> R.id.sourceFilterLanButton
            SourceFilter.CLOUD -> R.id.sourceFilterCloudButton
        })

        // Toolbar setup
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_lan_models -> {
                    parentFragmentManager.beginTransaction()
                        .replace(android.R.id.content, LanModelsFragment())
                        .addToBackStack(null).commit()
                    true
                }
                R.id.action_open_router_models -> {
                    parentFragmentManager.beginTransaction()
                        .replace(android.R.id.content, OpenRouterModelsFragment())
                        .addToBackStack(null).commit()
                    true
                }
                else -> false
            }
        }

        // Search Setup
        val searchItem = toolbar.menu.findItem(R.id.action_search)
        searchView = searchItem.actionView as SearchView
        searchView.queryHint = "Search models..."
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                filterAndSortModels(newText ?: "")
                return true
            }
        })

        // --- LISTENERS WITH PERSISTENCE ---
        sortBar.addOnButtonCheckedListener { _, checkedId, isChecked ->
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

        filterBar.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentFilterType = when (checkedId) {
                    R.id.filterAllButton -> FilterType.ALL
                    R.id.filterVisionButton -> FilterType.VISION
                    R.id.filterImageGenButton -> FilterType.IMAGE_GEN
                    else -> FilterType.ALL
                }
                sharedPreferencesHelper.saveBotPickerFilterType(currentFilterType.name)
                filterAndSortModels(searchView.query.toString())
            }
        }

        costFilterBar.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentCostFilter = when (checkedId) {
                    R.id.costFilterAllButton -> CostFilter.ALL
                    R.id.costFilterFreeButton -> CostFilter.FREE
                    R.id.costFilterPaidButton -> CostFilter.PAID
                    else -> CostFilter.ALL
                }
                sharedPreferencesHelper.saveBotPickerCostFilter(currentCostFilter.name)
                filterAndSortModels(searchView.query.toString())
            }
        }

        sourceFilterBar.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentSourceFilter = when (checkedId) {
                    R.id.sourceFilterAllButton -> SourceFilter.ALL
                    R.id.sourceFilterLanButton -> SourceFilter.LAN
                    R.id.sourceFilterCloudButton -> SourceFilter.CLOUD
                    else -> SourceFilter.ALL
                }
                sharedPreferencesHelper.saveBotPickerSourceFilter(currentSourceFilter.name)
                filterAndSortModels(searchView.query.toString())
            }
        }

        // Adapter and Observer setup
        adapter = BotModelAdapter(filteredModels, sharedPreferencesHelper.getPreferenceModelnew(),
            onItemClicked = { selectedModel ->
                onModelSelected?.invoke(selectedModel.apiIdentifier)
                parentFragmentManager.popBackStack()
            },
            onItemEdit = { showEditModelDialog(it) },
            onItemDelete = { showDeleteConfirmationDialog(it) }
        )
        recyclerView.adapter = adapter

        chatViewModel.customModelsUpdated.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                loadModels()
            }
        }

        loadModels()
        fabAddModel.setOnClickListener { showAddModelDialog() }
    }

    private fun loadModels() {
        val builtInModels = getModelsList()
        val customModels = sharedPreferencesHelper.getCustomModels()
        models.clear()
        models.addAll(builtInModels)
        models.addAll(customModels)
        filterAndSortModels(searchView.query.toString())
    }

    private fun filterAndSortModels(query: String) {
        var tempFiltered = models.toMutableList()

        tempFiltered = when (currentFilterType) {
            FilterType.ALL -> tempFiltered
            FilterType.VISION -> tempFiltered.filter { it.isVisionCapable }.toMutableList()
            FilterType.IMAGE_GEN -> tempFiltered.filter { it.isImageGenerationCapable }.toMutableList()
        }

        tempFiltered = when (currentCostFilter) {
            CostFilter.ALL -> tempFiltered
            CostFilter.FREE -> tempFiltered.filter { it.isLANModel || (!it.isLANModel && it.isFree) }.toMutableList()
            CostFilter.PAID -> tempFiltered.filter { !it.isLANModel && !it.isFree }.toMutableList()
        }

        tempFiltered = when (currentSourceFilter) {
            SourceFilter.ALL -> tempFiltered
            SourceFilter.LAN -> tempFiltered.filter { it.isLANModel }.toMutableList()
            SourceFilter.CLOUD -> tempFiltered.filter { !it.isLANModel }.toMutableList()
        }

        if (query.isNotEmpty()) {
            tempFiltered = tempFiltered.filter {
                it.displayName.contains(query, ignoreCase = true) || it.apiIdentifier.contains(query, ignoreCase = true)
            }.toMutableList()
        }

        when (currentSortOrder) {
            SortOrder.ALPHABETICAL -> tempFiltered.sortBy { it.displayName.lowercase() }
            SortOrder.BY_DATE -> tempFiltered.sortByDescending { it.created }
        }

        filteredModels = tempFiltered
        adapter.updateModels(filteredModels)
    }

    private fun showAddModelDialog() {
        val dialog = EditModelDialogFragment()
        dialog.onModelAdded = { addModel(it) }
        dialog.show(parentFragmentManager, "add_model_dialog")
    }

    private fun showEditModelDialog(modelToEdit: LlmModel) {
        val dialog = EditModelDialogFragment().apply {
            arguments = Bundle().apply {
                putString("displayName", modelToEdit.displayName)
                putString("apiIdentifier", modelToEdit.apiIdentifier)
                putBoolean("isVisionCapable", modelToEdit.isVisionCapable)
                putBoolean("isReasoningCapable", modelToEdit.isReasoningCapable)
                putBoolean("isImageGenerationCapable", modelToEdit.isImageGenerationCapable)
                putLong("created", modelToEdit.created)
                putBoolean("isLANModel", modelToEdit.isLANModel)
                putBoolean("isFree", modelToEdit.isFree)
            }
        }
        dialog.onModelUpdated = { old, new -> updateModel(old, new) }
        dialog.show(parentFragmentManager, "edit_model_dialog")
    }

    private fun showDeleteConfirmationDialog(model: LlmModel) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Model")
            .setMessage("Are you sure you want to delete '${model.displayName}'?")
            .setPositiveButton("Delete") { _, _ -> deleteModel(model) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addModel(model: LlmModel) {
        if (models.any { it.apiIdentifier.equals(model.apiIdentifier, ignoreCase = true) }) {
            Toast.makeText(context, "Model exists.", Toast.LENGTH_SHORT).show()
        } else {
            models.add(model)
            saveCustomModels()
            filterAndSortModels(searchView.query.toString())
        }
    }

    private fun updateModel(oldModel: LlmModel, newModel: LlmModel) {
        val index = models.indexOfFirst { it.apiIdentifier == oldModel.apiIdentifier }
        if (index != -1) {
            if (oldModel.apiIdentifier == sharedPreferencesHelper.getPreferenceModelnew()) {
                sharedPreferencesHelper.savePreferenceModelnewchat(newModel.apiIdentifier)
                chatViewModel.setModel(newModel.apiIdentifier)
            }
            models[index] = newModel
            saveCustomModels()
            filterAndSortModels(searchView.query.toString())
        }
    }

    private fun deleteModel(model: LlmModel) {
        if (model.apiIdentifier == sharedPreferencesHelper.getPreferenceModelnew()) {
            val default = "openrouter/free"
            sharedPreferencesHelper.savePreferenceModelnewchat(default)
            chatViewModel.setModel(default)
            adapter.updateCurrentModel(default)
        }
        models.remove(model)
        saveCustomModels()
        filterAndSortModels(searchView.query.toString())
    }

    private fun saveCustomModels() {
        val builtInIds = getModelsList().map { it.apiIdentifier }
        val custom = models.filter { !builtInIds.contains(it.apiIdentifier) }
        sharedPreferencesHelper.saveCustomModels(custom)
    }

    private fun getModelsList() = listOf(
        LlmModel(
            displayName = "OpenRouter: Free",
            apiIdentifier = "openrouter/free",
            isVisionCapable = true,
            isReasoningCapable = true,
            isFree = true
        )
    )
    private fun updateSortButtons(sortOrder: SortOrder) {
        sortBar.check(if (sortOrder == SortOrder.ALPHABETICAL) R.id.sortAlphabeticalButton else R.id.sortDateButton)
    }
}
