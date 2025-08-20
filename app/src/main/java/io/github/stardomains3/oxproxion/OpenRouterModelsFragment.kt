package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OpenRouterModelsFragment : Fragment() {

    private lateinit var viewModel: ChatViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: OpenRouterModelsAdapter
    private lateinit var sharedPreferencesHelper: SharedPreferencesHelper
    //private lateinit var sortAlphabeticalButton: MaterialButton
    //private lateinit var sortDateButton: MaterialButton
    private lateinit var sortBar: MaterialButtonToggleGroup

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

        viewModel = ViewModelProvider(requireActivity()).get(ChatViewModel::class.java)
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

        val infoCard = view.findViewById<CardView>(R.id.infoCard)
        val closeInfoButton = view.findViewById<ImageView>(R.id.closeInfoButton)
        // sortAlphabeticalButton = view.findViewById(R.id.sortAlphabeticalButton)
        //sortDateButton = view.findViewById(R.id.sortDateButton)
        sortBar = view.findViewById<MaterialButtonToggleGroup>(R.id.sortBar)

        if (sharedPreferencesHelper.hasDismissedOpenRouterInfo()) {
            infoCard.visibility = View.GONE
        } else {
            infoCard.visibility = View.VISIBLE
        }

        closeInfoButton.setOnClickListener {
            infoCard.visibility = View.GONE
            sharedPreferencesHelper.setOpenRouterInfoDismissed(true)
        }

        /*sortAlphabeticalButton.setOnClickListener {
            viewModel.setSortOrder(SortOrder.ALPHABETICAL)
        }
        sortDateButton.setOnClickListener {
            viewModel.setSortOrder(SortOrder.BY_DATE)
        }*/
        sortBar.addOnButtonCheckedListener { group, checkedId, isChecked ->
            // This listener is called when a button's checked state changes.
            // It's called for the button being UNCHECKED (isChecked = false)
            // and for the button being CHECKED (isChecked = true).
            // We only care about the one that was just checked.
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
            // Add the model to the custom list
            addModel(model)
        }
        recyclerView.adapter = adapter

        viewModel.openRouterModels.observe(viewLifecycleOwner) { models ->
            adapter.updateModels(models)
        }

        lifecycleScope.launch {
            viewModel.sortOrder.collectLatest { sortOrder ->
                updateSortButtons(sortOrder)
            }
        }

        // Load initial models from shared preferences, or fetch if empty
        viewModel.getOpenRouterModels()
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
