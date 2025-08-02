package io.github.stardomains3.oxproxion


object ChatServiceGate {
    @Volatile
    var shouldRunService: Boolean = false
}