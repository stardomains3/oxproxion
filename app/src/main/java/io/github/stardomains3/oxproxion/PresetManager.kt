package io.github.stardomains3.oxproxion

import android.content.Context

object PresetManager {
    fun applyPreset(context: Context, viewModel: ChatViewModel, preset: Preset) {
        val prefs = SharedPreferencesHelper(context)

        // Apply model
        viewModel.setModel(preset.modelIdentifier)
        prefs.savePreferenceModelnewchat(preset.modelIdentifier)

        // Apply system message (it was already validated to exist before calling this)
        prefs.saveSelectedSystemMessage(preset.systemMessage)

        // Apply streaming/reasoning/conversation toggles
        prefs.saveStreamingPreference(preset.streaming)
        prefs.saveReasoningPreference(preset.reasoning)
        prefs.saveConversationModeEnabled(preset.conversationMode)
        prefs.saveToolsPreference(preset.tools)
        viewModel._isToolsEnabled.value = preset.tools
        viewModel._isStreamingEnabled.value = preset.streaming
        viewModel._isReasoningEnabled.value = preset.reasoning
        prefs.saveWebSearchEnabled(preset.webSearch)
        viewModel._isWebSearchEnabled.value = preset.webSearch


        // Update VM LiveData (buttons states)
        // Conversation mode button will be synced in ChatFragment.onHiddenChanged, but we can set it here too:
        // The ChatFragment observes prefs and updates button state on resume. For immediate UI:
        // No direct VM state for conversation mode; it's read from prefs in ChatFragment.onHiddenChanged.
        // If you have a VM property for conversation mode, set it here as well.
    }
    /**
     * Captures current settings before applying assistant preset.
     * Returns a snapshot that can be restored later.
     */
    fun captureCurrentState(context: Context, viewModel: ChatViewModel): AssistantStateSnapshot {
        val prefs = SharedPreferencesHelper(context)
        return AssistantStateSnapshot(
            modelIdentifier = prefs.getPreferenceModelnew(),
            systemMessage = prefs.getSelectedSystemMessage(),
            streaming = prefs.getStreamingPreference(),
            reasoning = prefs.getReasoningPreference(),
            conversationMode = prefs.getConversationModeEnabled(),
            tools = prefs.getToolsPreference(),
            webSearch = prefs.getWebSearchBoolean()
        )
    }

    /**
     * Restores settings from a snapshot (called when assistant activity is destroyed).
     */
    fun restoreState(context: Context, viewModel: ChatViewModel, snapshot: AssistantStateSnapshot) {
        val prefs = SharedPreferencesHelper(context)

        // Restore model
        viewModel.setModel(snapshot.modelIdentifier)
        prefs.savePreferenceModelnewchat(snapshot.modelIdentifier)

        // Restore system message
        prefs.saveSelectedSystemMessage(snapshot.systemMessage)

        // Restore toggles
        prefs.saveStreamingPreference(snapshot.streaming)
        prefs.saveReasoningPreference(snapshot.reasoning)
        prefs.saveConversationModeEnabled(snapshot.conversationMode)
        prefs.saveToolsPreference(snapshot.tools)
        viewModel._isToolsEnabled.value = snapshot.tools
        viewModel._isStreamingEnabled.value = snapshot.streaming
        viewModel._isReasoningEnabled.value = snapshot.reasoning
        prefs.saveWebSearchEnabled(snapshot.webSearch)
        viewModel._isWebSearchEnabled.value = snapshot.webSearch
    }

}
data class AssistantStateSnapshot(
    val modelIdentifier: String,
    val systemMessage: SystemMessage,
    val streaming: Boolean,
    val reasoning: Boolean,
    val conversationMode: Boolean,
    val tools: Boolean,
    val webSearch: Boolean
)