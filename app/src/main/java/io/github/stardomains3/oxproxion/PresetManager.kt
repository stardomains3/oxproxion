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
        viewModel._isStreamingEnabled.value = preset.streaming
        viewModel._isReasoningEnabled.value = preset.reasoning


        // Update VM LiveData (buttons states)
        // Conversation mode button will be synced in ChatFragment.onHiddenChanged, but we can set it here too:
        // The ChatFragment observes prefs and updates button state on resume. For immediate UI:
        // No direct VM state for conversation mode; it's read from prefs in ChatFragment.onHiddenChanged.
        // If you have a VM property for conversation mode, set it here as well.
    }
}