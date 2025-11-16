package io.github.stardomains3.oxproxion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import kotlin.collections.sortedBy
import kotlin.jvm.java
import kotlin.text.lowercase

class LanModelsFragment : Fragment() {

    private lateinit var viewModel: ChatViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LanModelsAdapter
    private var allModels: List<LlmModel> = emptyList()

    companion object {
        const val TAG = "LanModelsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_lan_models, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val provider = viewModel.getCurrentLanProvider()
        val title = when (provider) {
            "lm_studio" -> "LM Studio Models"
            "llama_cpp" -> "llama.cpp Models" // NEW
            "ollama" -> "Ollama Models"
            else -> "LAN Models"
        }
        toolbar.title = title
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_refresh_models -> {
                    val provider = viewModel.getCurrentLanProvider()
                    val providerName = when (provider) { // UPDATED
                        "lm_studio" -> "LM Studio"
                        "llama_cpp" -> "llama.cpp" // NEW
                        "ollama" -> "Ollama"
                        else -> "LAN"
                    }
                 //   Toast.makeText(requireContext(), "Refreshing $providerName models…", Toast.LENGTH_SHORT).show()
                    fetchLanModels()
                    true
                }
                else -> false
            }
        }


        recyclerView = view.findViewById(R.id.recyclerViewLanModels)
        recyclerView.layoutManager = LinearLayoutManager(context)

        adapter = LanModelsAdapter(emptyList()) { model ->
            addModel(model)
        }
        recyclerView.adapter = adapter

        // Load models when fragment is created
        fetchLanModels()
    }

    private fun fetchLanModels() {
        lifecycleScope.launch {
            try {
                val provider = viewModel.getCurrentLanProvider()
                val models = viewModel.fetchLanModels()

                if (models.isEmpty()) {
                    val emptyMessage = when (provider) { // UPDATED
                        "lm_studio" -> "No LM Studio models found.\nMake sure LM Studio is running and has models loaded."
                        "llama_cpp" -> "No llama.cpp models found.\nMake sure llama.cpp server is running and has models loaded." // NEW
                        "ollama" -> "No Ollama models found.\nMake sure Ollama is installed and has models pulled."
                        else -> "No models found."
                    }
                    Toast.makeText(requireContext(), emptyMessage, Toast.LENGTH_LONG).show()
                } else {
                  //  val successMessage = "Found ${models.size} ${provider} model${if (models.size != 1) "s" else ""}"
                   // Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show()
                }

                allModels = models.sortedBy { it.displayName.lowercase() }
                adapter.updateModels(allModels)
            } catch (e: Exception) {
                val provider = viewModel.getCurrentLanProvider()
                val errorMessage = when (provider) { // UPDATED
                    "lm_studio" -> "Failed to fetch LM Studio models: ${e.message}"
                    "llama_cpp" -> "Failed to fetch llama.cpp models: ${e.message}" // NEW
                    "ollama" -> "Failed to fetch Ollama models: ${e.message}"
                    else -> "Failed to fetch LAN models: ${e.message}"
                }
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }


    private fun addModel(model: LlmModel) {
        if (viewModel.modelExists(model.apiIdentifier)) {
            Toast.makeText(context, "Model with this API Identifier already exists.", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.addCustomModel(model)
            val provider = viewModel.getCurrentLanProvider()
            Toast.makeText(context, "LAN Model added: ${model.displayName}", Toast.LENGTH_SHORT).show()
        }
    }
}
