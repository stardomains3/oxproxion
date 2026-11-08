package io.github.stardomains3.oxproxion

import kotlinx.serialization.Serializable

@Serializable
data class LlmModel(
    val displayName: String,      // User-friendly name, e.g., "GPT-4o"
    val apiIdentifier: String,    // The actual model string, e.g., "openai/gpt-4o-mini"
    val isVisionCapable: Boolean,  // To determine which icon to show
    val isImageGenerationCapable: Boolean = false,
    val isPureImageModel: Boolean = false,  // Output is ONLY images (no text) — must use /api/v1/images
    val isReasoningCapable: Boolean = false,
    val isTranscription: Boolean = false,  // NEW - for audio transcription models
    val created: Long = 0L,
    val isFree: Boolean = apiIdentifier.endsWith(":free")||apiIdentifier.endsWith("/free"),
    val isLANModel: Boolean = false,
    val isLoaded: Boolean = false  // Used by llama.cpp to indicate if model is loaded
)