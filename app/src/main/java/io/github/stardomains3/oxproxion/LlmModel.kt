package io.github.stardomains3.oxproxion

data class LlmModel(
    val displayName: String,      // User-friendly name, e.g., "GPT-4o"
    val apiIdentifier: String,    // The actual model string, e.g., "openai/gpt-4o-mini"
    val isVisionCapable: Boolean  // To determine which icon to show
)
