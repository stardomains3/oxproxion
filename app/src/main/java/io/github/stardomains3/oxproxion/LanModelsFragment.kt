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
            "llama_cpp" -> "llama.cpp Models"
            "mlx_lm" -> "MLX LM Models"
            "ollama" -> "Ollama Models"
            else -> "LAN Models"
        }
        toolbar.title = title

        // CANCEL BEFORE BACK
        toolbar.setNavigationOnClickListener {
            viewModel.cancelCurrentRequest()
            parentFragmentManager.popBackStack()
        }

        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_refresh_models -> {
                    viewModel.startLanModelsFetch()
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

        // OBSERVE MODELS (REACTIVE)
        viewModel.lanModels.observe(viewLifecycleOwner) { models ->
            allModels = models.sortedBy { it.displayName.lowercase() }
            adapter.updateModels(allModels)

            // EMPTY TOAST
            if (models.isEmpty()) {
                val emptyMessage = when (provider) {
                    "lm_studio" -> "No LM Studio models found.\nMake sure LM Studio is running and has models loaded."
                    "llama_cpp" -> "No llama.cpp models found.\nMake sure llama.cpp server is running and has models loaded."
                    "mlx_lm" -> "No MLX LM models found.\nMake sure MLX LM server is running and has models loaded."
                    "ollama" -> "No Ollama models found.\nMake sure Ollama is installed and has models pulled."
                    else -> "No models found."
                }
                Toast.makeText(requireContext(), emptyMessage, Toast.LENGTH_LONG).show()
            }
        }

        // OBSERVE ERRORS
        viewModel.toolUiEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }

        // START FETCH
        viewModel.startLanModelsFetch()
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

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.cancelCurrentRequest()
    }
}