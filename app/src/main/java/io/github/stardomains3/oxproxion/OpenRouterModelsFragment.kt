package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OpenRouterModelsFragment : Fragment() {

    private lateinit var viewModel: ChatViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: OpenRouterModelsAdapter
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    private lateinit var sortBar: MaterialButtonToggleGroup
    private lateinit var searchView: SearchView
    private lateinit var filterBar: MaterialButtonToggleGroup
    private lateinit var costFilterBar: MaterialButtonToggleGroup
    private var allModels: List<LlmModel> = emptyList()
    private var filteredModels: List<LlmModel> = emptyList()
    private var currentFilterType: FilterType = FilterType.ALL
    private var currentCostFilter: CostFilter = CostFilter.ALL

    enum class FilterType {
        ALL, VISION, IMAGE_GEN
    }

    enum class CostFilter {
        ALL, FREE, PAID
    }

    companion object {
        const val TAG = "OpenRouterModelsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_open_router_models, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
        sharedPreferencesHelper = SharedPreferencesHelper(requireContext())

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_refresh_models -> {
                    Toast.makeText(requireContext(), "Refreshing models…", Toast.LENGTH_SHORT).show()
                    viewModel.fetchOpenRouterModels()
                    true
                }
                else -> false
            }
        }

        val searchItem = toolbar.menu.findItem(R.id.action_search)
        searchView = searchItem.actionView as SearchView
        searchView.queryHint = "Search models..."
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                filterModels(newText ?: "")
                return true
            }
        })

        val infoCard = view.findViewById<CardView>(R.id.infoCard)
        val closeInfoButton = view.findViewById<ImageView>(R.id.closeInfoButton)
        sortBar = view.findViewById(R.id.sortBar)
        filterBar = view.findViewById(R.id.filterBar)
        costFilterBar = view.findViewById(R.id.costFilterBar)

        if (sharedPreferencesHelper.hasDismissedOpenRouterInfo()) {
            infoCard.visibility = View.GONE
        } else {
            infoCard.visibility = View.VISIBLE
        }

        closeInfoButton.setOnClickListener {
            infoCard.visibility = View.GONE
            sharedPreferencesHelper.setOpenRouterInfoDismissed(true)
        }

        // --- Persistent Filter Logic Starts Here ---

        // 1. Load saved states from SharedPreferences
        currentFilterType = when (sharedPreferencesHelper.getOpenRouterFilterType()) {
            "VISION" -> FilterType.VISION
            "IMAGE_GEN" -> FilterType.IMAGE_GEN
            else -> FilterType.ALL
        }
        currentCostFilter = when (sharedPreferencesHelper.getOpenRouterCostFilter()) {
            "FREE" -> CostFilter.FREE
            "PAID" -> CostFilter.PAID
            else -> CostFilter.ALL
        }

        // 2. Visually check the correct buttons based on loaded state
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

        // 3. Listeners that save the new selection
        sortBar.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.sortAlphabeticalButton -> viewModel.setSortOrder(SortOrder.ALPHABETICAL)
                    R.id.sortDateButton -> viewModel.setSortOrder(SortOrder.BY_DATE)
                }
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
                sharedPreferencesHelper.saveOpenRouterFilterType(currentFilterType.name)
                filterModels(searchView.query.toString())
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
                sharedPreferencesHelper.saveOpenRouterCostFilter(currentCostFilter.name)
                filterModels(searchView.query.toString())
            }
        }

        recyclerView = view.findViewById(R.id.recyclerViewOpenRouterModels)
        recyclerView.layoutManager = LinearLayoutManager(context)

        adapter = OpenRouterModelsAdapter(emptyList()) { model ->
            addModel(model)
        }
        recyclerView.adapter = adapter

        viewModel.openRouterModels.observe(viewLifecycleOwner) { models ->
            allModels = models
            filterModels(searchView.query.toString())
        }

        lifecycleScope.launch {
            viewModel.sortOrder.collectLatest { sortOrder ->
                updateSortButtons(sortOrder)
                filterModels(searchView.query.toString())
            }
        }

        viewModel.getOpenRouterModels()
    }

    private fun filterModels(query: String) {
        var tempFiltered = allModels

        tempFiltered = when (currentFilterType) {
            FilterType.ALL -> tempFiltered
            FilterType.VISION -> tempFiltered.filter { it.isVisionCapable }
            FilterType.IMAGE_GEN -> tempFiltered.filter { it.isImageGenerationCapable }
        }

        tempFiltered = when (currentCostFilter) {
            CostFilter.ALL -> tempFiltered
            CostFilter.FREE -> tempFiltered.filter { it.isFree }
            CostFilter.PAID -> tempFiltered.filter { !it.isFree }
        }

        tempFiltered = if (query.isEmpty()) {
            tempFiltered
        } else {
            tempFiltered.filter { model ->
                model.displayName.contains(query, ignoreCase = true) ||
                        model.apiIdentifier.contains(query, ignoreCase = true)
            }
        }

        filteredModels = tempFiltered
        adapter.updateModels(filteredModels)
    }

    private fun updateSortButtons(sortOrder: SortOrder) {
        when (sortOrder) {
            SortOrder.ALPHABETICAL -> sortBar.check(R.id.sortAlphabeticalButton)
            SortOrder.BY_DATE -> sortBar.check(R.id.sortDateButton)
        }
    }

    private fun addModel(model: LlmModel) {
        if (viewModel.modelExists(model.apiIdentifier)) {
            Toast.makeText(context, "Model already exists.", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.addCustomModel(model)
            Toast.makeText(context, "Model added: ${model.displayName}", Toast.LENGTH_SHORT).show()
        }
    }
}