package io.github.stardomains3.oxproxion

data class SystemMessage(
    val title: String,
    val prompt: String,
    val isDefault: Boolean = false
)
