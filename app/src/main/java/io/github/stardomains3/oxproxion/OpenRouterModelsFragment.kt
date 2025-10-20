package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.widget.SearchView  // NEW: Import for SearchView
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
    private lateinit var searchView: SearchView  // NEW: Reference to SearchView
    private var allModels: List<LlmModel> = emptyList()  // NEW: Store the full unfiltered list
    private var filteredModels: List<LlmModel> = emptyList()  // NEW: Store the filtered list
    private lateinit var filterBar: MaterialButtonToggleGroup
    private var currentFilterType: FilterType = FilterType.ALL

    companion object {
        const val TAG = "OpenRouterModelsFragment"
    }
    enum class FilterType {
        ALL, VISION, IMAGE_GEN
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
                R.id.action_search -> {
                    // NEW: Handle search expansion (optional, as SearchView handles it automatically)
                    true
                }
                else -> false
            }
        }

        // NEW: Initialize SearchView from the menu
        val searchItem = toolbar.menu.findItem(R.id.action_search)
        searchView = searchItem.actionView as SearchView
        searchView.queryHint = "Search models..."  // Hint text for users
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Optional: Handle submit (e.g., for keyboard enter), but filtering is real-time
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterModels(newText ?: "")  // NEW: Filter as user types
                return true
            }
        })

        // Rest of your existing code...
        val infoCard = view.findViewById<CardView>(R.id.infoCard)
        val closeInfoButton = view.findViewById<ImageView>(R.id.closeInfoButton)
        sortBar = view.findViewById(R.id.sortBar)
        filterBar = view.findViewById(R.id.filterBar)
        filterBar.check(R.id.filterAllButton)
        if (sharedPreferencesHelper.hasDismissedOpenRouterInfo()) {
            infoCard.visibility = View.GONE
        } else {
            infoCard.visibility = View.VISIBLE
        }

        closeInfoButton.setOnClickListener {
            infoCard.visibility = View.GONE
            sharedPreferencesHelper.setOpenRouterInfoDismissed(true)
        }
        filterBar.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                currentFilterType = when (checkedId) {
                    R.id.filterAllButton -> FilterType.ALL
                    R.id.filterVisionButton -> FilterType.VISION
                    R.id.filterImageGenButton -> FilterType.IMAGE_GEN
                    else -> FilterType.ALL
                }
                filterModels(searchView.query.toString())  // Re-filter with current search
            }
        }
        sortBar.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.sortAlphabeticalButton -> {
                        viewModel.setSortOrder(SortOrder.ALPHABETICAL)
                    }
                    R.id.sortDateButton -> {
                        viewModel.setSortOrder(SortOrder.BY_DATE)
                    }
                }
            }
        }

        recyclerView = view.findViewById(R.id.recyclerViewOpenRouterModels)
        recyclerView.layoutManager = LinearLayoutManager(context)

        adapter = OpenRouterModelsAdapter(emptyList()) { model ->
            addModel(model)
        }
        recyclerView.adapter = adapter

        // NEW: Observe the full models list and store it
        viewModel.openRouterModels.observe(viewLifecycleOwner) { models ->
            allModels = models  // Store the full list
            filterModels(searchView.query.toString())  // Apply current search filter
        }

        lifecycleScope.launch {
            viewModel.sortOrder.collectLatest { sortOrder ->
                updateSortButtons(sortOrder)
                // NEW: Re-filter after sort change to maintain sort in filtered results
                filterModels(searchView.query.toString())
            }
        }

        viewModel.getOpenRouterModels()
    }

    // UPDATED: Method to filter models based on query and type
    private fun filterModels(query: String) {
        var tempFiltered = allModels  // Start with the full (sorted) list

        // Apply type filter
        tempFiltered = when (currentFilterType) {
            FilterType.ALL -> tempFiltered
            FilterType.VISION -> tempFiltered.filter { it.isVisionCapable }
            FilterType.IMAGE_GEN -> tempFiltered.filter { it.isImageGenerationCapable }
        }

        // Apply search filter
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
            SortOrder.ALPHABETICAL -> {
                sortBar.check(R.id.sortAlphabeticalButton)
            }
            SortOrder.BY_DATE -> {
                sortBar.check(R.id.sortDateButton)
            }
        }
    }

    private fun addModel(model: LlmModel) {
        if (viewModel.modelExists(model.apiIdentifier)) {
            Toast.makeText(context, "Model with this API Identifier already exists.", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.addCustomModel(model)
            Toast.makeText(context, "Model added: ${model.displayName}", Toast.LENGTH_SHORT).show()
        }
    }
}
