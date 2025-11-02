package io.github.stardomains3.oxproxion

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText

class PresetEditFragment : Fragment() {

    private lateinit var viewModel: ChatViewModel
    private lateinit var prefs: SharedPreferencesHelper
    private lateinit var toolbar: MaterialToolbar

    private var editingPreset: Preset? = null

    private lateinit var titleInput: TextInputEditText
    private lateinit var modelAutoComplete: MaterialAutoCompleteTextView
    private lateinit var systemMessageAutoComplete: MaterialAutoCompleteTextView
    private lateinit var streamingSwitch: MaterialSwitch
    private lateinit var reasoningSwitch: MaterialSwitch
    private lateinit var conversationSwitch: MaterialSwitch
    private lateinit var saveBtn: MaterialButton
    private lateinit var cancelBtn: MaterialButton

    private var selectedModelIdentifier: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_preset_edit, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(requireActivity())[ChatViewModel::class.java]
        prefs = SharedPreferencesHelper(requireContext())

        initViews(view)
        setupToolbar()

        // Load preset for editing if available
        val currentPreset = arguments?.getString(ARG_PRESET_ID)?.let { id ->
            PresetRepository(requireContext()).findById(id)
        }
        editingPreset = currentPreset

        // Setup AutoComplete dropdowns
        setupModelAutoComplete()
        setupSystemMessageAutoComplete()

        // Load data if editing
        if (editingPreset != null) {
            populateEditData(editingPreset!!)
        }

        // Setup click listeners
        saveBtn.setOnClickListener { save() }
        cancelBtn.setOnClickListener { parentFragmentManager.popBackStack() }
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



        streamingSwitch.trackTintList = trackTintSelector
        streamingSwitch.thumbTintList = thumbTintSelector
        streamingSwitch.thumbTintMode = PorterDuff.Mode.SRC_ATOP
        streamingSwitch.trackTintMode = PorterDuff.Mode.SRC_ATOP

        reasoningSwitch.trackTintList = trackTintSelector
        reasoningSwitch.thumbTintList = thumbTintSelector
        reasoningSwitch.thumbTintMode = PorterDuff.Mode.SRC_ATOP
        reasoningSwitch.trackTintMode = PorterDuff.Mode.SRC_ATOP

        conversationSwitch.trackTintList = trackTintSelector
        conversationSwitch.thumbTintList = thumbTintSelector
        conversationSwitch.thumbTintMode = PorterDuff.Mode.SRC_ATOP
        conversationSwitch.trackTintMode = PorterDuff.Mode.SRC_ATOP
    }

    private fun initViews(view: View) {
        toolbar = view.findViewById(R.id.toolbar)
        titleInput = view.findViewById(R.id.editPresetTitle)
        modelAutoComplete = view.findViewById(R.id.autoCompleteModel)
        systemMessageAutoComplete = view.findViewById(R.id.autoCompleteSystemMessage)
        streamingSwitch = view.findViewById(R.id.switchStreaming)
        reasoningSwitch = view.findViewById(R.id.switchReasoning)
        conversationSwitch = view.findViewById(R.id.switchConversation)
        saveBtn = view.findViewById(R.id.buttonSave)
        cancelBtn = view.findViewById(R.id.buttonCancel)

        streamingSwitch.isChecked = false // default: off
        reasoningSwitch.isChecked = true // default: on
        conversationSwitch.isChecked = false // default: off
    }

    private fun setupToolbar() {
        toolbar.title = if (editingPreset == null) "Create Preset" else "Edit Preset"
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupModelAutoComplete() {
        val allModels = getAllModels()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, allModels.map { it.displayName })
        modelAutoComplete.setAdapter(adapter)

        // Use TextWatcher for immediate updates (like working EditPresetFragment)
        modelAutoComplete.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val selectedModelName = s?.toString()?.trim().orEmpty()
                if (selectedModelName.isNotEmpty()) {
                    val allModels = getAllModels()
                    val selectedModel = allModels.find { it.displayName == selectedModelName }
                    selectedModel?.let {
                        selectedModelIdentifier = it.apiIdentifier
                        updateReasoningVisibility()
                    }
                }
            }
        })
    }

    private fun setupSystemMessageAutoComplete() {
        val allMessages = getAllSystemMessages()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, allMessages.map { it.title })
        systemMessageAutoComplete.setAdapter(adapter)
    }

    private fun populateEditData(preset: Preset) {
        titleInput.setText(preset.title)

        // Set model text
        val allModels = getAllModels()
        val modelDisplayName = allModels.find { it.apiIdentifier.equals(preset.modelIdentifier, ignoreCase = true) }?.displayName
            ?: "Missing: ${preset.modelIdentifier}"
        modelAutoComplete.setText(modelDisplayName, false)
        selectedModelIdentifier = preset.modelIdentifier

        // Set system message text
        val allMessages = getAllSystemMessages()
        val systemMessageTitle = allMessages.find {
            it.title == preset.systemMessage.title && it.prompt == preset.systemMessage.prompt
        }?.title ?: preset.systemMessage.title
        systemMessageAutoComplete.setText(systemMessageTitle, false)

        // Set toggles
        streamingSwitch.isChecked = preset.streaming
        reasoningSwitch.isChecked = preset.reasoning
        conversationSwitch.isChecked = preset.conversationMode

        // Update reasoning visibility based on selected model
        updateReasoningVisibility()
    }

    private fun updateReasoningVisibility() {
        val modelId = selectedModelIdentifier ?: return
        val isReasoning = viewModel.isReasoningModel(modelId)
        reasoningSwitch.visibility = if (isReasoning) View.VISIBLE else View.GONE
    }

    private fun getAllModels(): List<LlmModel> {
        val builtIn = viewModel.getBuiltInModels()
        val custom = prefs.getCustomModels()
        return (builtIn + custom).distinctBy { it.apiIdentifier.lowercase() }
            .sortedBy { it.displayName.lowercase() }
    }

    private fun getAllSystemMessages(): List<SystemMessage> {
        val default = prefs.getDefaultSystemMessage()
        val custom = prefs.getCustomSystemMessages()
        return (listOf(default) + custom).sortedBy { it.title.lowercase() }
    }

    private fun getSelectedModel(): LlmModel? {
        val modelName = modelAutoComplete.text.toString().trim()
        return getAllModels().find { it.displayName == modelName }
    }

    private fun getSelectedSystemMessage(): SystemMessage? {
        val messageTitle = systemMessageAutoComplete.text.toString().trim()
        return getAllSystemMessages().find { it.title == messageTitle }
    }

    private fun save() {
        val title = titleInput.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            titleInput.error = "Title is required"
            return
        }

        val model = getSelectedModel()
        if (model == null) {
            // Show error but don't block - allow saving with missing model (shows as "Missing: ...")
            val modelName = modelAutoComplete.text.toString().trim()
            if (modelName.isEmpty()) {
                // Still empty, show error
                return
            }
        }

        val sysMsg = getSelectedSystemMessage() ?: prefs.getDefaultSystemMessage()

        val streaming = streamingSwitch.isChecked
        val reasoning = if (reasoningSwitch.visibility == View.VISIBLE) reasoningSwitch.isChecked else false
        val conversationMode = conversationSwitch.isChecked

        val preset = Preset(
            id = editingPreset?.id ?: java.util.UUID.randomUUID().toString(),
            title = title,
            modelIdentifier = model?.apiIdentifier ?: "unknown-model",
            systemMessage = sysMsg,
            streaming = streaming,
            reasoning = reasoning,
            conversationMode = conversationMode
        )

        val repo = PresetRepository(requireContext())
        repo.upsert(preset)

        android.widget.Toast.makeText(requireContext(),
            if (editingPreset == null) "Preset created" else "Preset saved",
            android.widget.Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
    }

    companion object {
        private const val ARG_PRESET_ID = "preset_id"
        fun newInstance(preset: Preset?): PresetEditFragment {
            return PresetEditFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PRESET_ID, preset?.id)
                }
            }
        }
    }
}
