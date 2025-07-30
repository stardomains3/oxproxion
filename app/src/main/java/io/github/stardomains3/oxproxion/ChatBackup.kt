package io.github.stardomains3.oxproxion

import kotlinx.serialization.Serializable

@Serializable
data class ChatBackup(
    val sessions: List<ExportedChatSession>
)

@Serializable
data class ExportedChatSession(
    val title: String,
    val modelUsed: String,
    val messages: List<ExportedChatMessage>
)

@Serializable
data class ExportedChatMessage(
    val role: String,
    val content: String // The raw JSON content from the database
)
