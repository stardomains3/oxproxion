package io.github.stardomains3.oxproxion

import kotlinx.serialization.Serializable

@Serializable
data class SystemMessage(
    val title: String,
    val prompt: String,
    val isDefault: Boolean = false
)
