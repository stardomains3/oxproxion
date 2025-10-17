package io.github.stardomains3.oxproxion

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class SystemMessage(
    val title: String,
    val prompt: String,
    val isDefault: Boolean = false,
    @Transient
    var isExpanded: Boolean = false
)

