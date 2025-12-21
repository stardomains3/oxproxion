package io.github.stardomains3.oxproxion

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Preset(
    val id: String,
    val title: String,
    val modelIdentifier: String,
    val systemMessage: SystemMessage,
    val streaming: Boolean,
    val reasoning: Boolean,
    val conversationMode: Boolean,
    val webSearch: Boolean = false,
    // UI‑only flag – will NOT be serialised or persisted
    @Transient
    var isExpanded: Boolean = false
)