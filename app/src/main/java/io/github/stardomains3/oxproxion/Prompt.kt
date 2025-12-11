package io.github.stardomains3.oxproxion

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Prompt(
    val title: String,
    val prompt: String,
    @Transient  // ✅ CRITICAL: Prevents serialization of UI state (matches SystemMessage)
    var isExpanded: Boolean = false
)