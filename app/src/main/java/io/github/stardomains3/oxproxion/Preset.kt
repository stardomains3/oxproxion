package io.github.stardomains3.oxproxion

import kotlinx.serialization.Serializable

@Serializable
data class Preset(
    val id: String,
    val title: String,
    val modelIdentifier: String,
    val systemMessage: SystemMessage,
    val streaming: Boolean,
    val reasoning: Boolean,
    val conversationMode: Boolean
)