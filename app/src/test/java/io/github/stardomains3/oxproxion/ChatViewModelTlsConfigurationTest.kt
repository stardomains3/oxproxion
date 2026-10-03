package io.github.stardomains3.oxproxion

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatViewModelTlsConfigurationTest {

    @Test
    fun createLanHttpClientUsesPlatformTlsValidation() {
        val factorySource = readLanClientFactorySource()

        assertFalse(
            "createLanHttpClient must not declare a trust-all X509TrustManager",
            factorySource.contains("object : X509TrustManager")
        )
        assertFalse(
            "createLanHttpClient must not initialize a custom SSLContext",
            factorySource.contains("SSLContext.getInstance(")
        )
        assertFalse(
            "createLanHttpClient must not configure a custom SSL socket factory",
            factorySource.contains("sslSocketFactory(")
        )
        assertFalse(
            "createLanHttpClient must not configure an always-true hostname verifier",
            Regex("""hostnameVerifier\s*\{\s*[^}]*->\s*true\s*}""").containsMatchIn(factorySource)
        )
    }

    private fun readLanClientFactorySource(): String {
        val sourceFile = listOf(
            File("src/main/java/io/github/stardomains3/oxproxion/ChatViewModel.kt"),
            File("app/src/main/java/io/github/stardomains3/oxproxion/ChatViewModel.kt")
        ).firstOrNull(File::isFile)
            ?: throw AssertionError("Unable to locate ChatViewModel.kt from the test working directory")
        val source = sourceFile.readText()
        val declarationStart = source.indexOf("fun createLanHttpClient")
        if (declarationStart < 0) {
            throw AssertionError("Unable to locate createLanHttpClient in ChatViewModel.kt")
        }

        val openingBrace = source.indexOf('{', declarationStart)
        if (openingBrace < 0) {
            throw AssertionError("Unable to locate createLanHttpClient body")
        }

        var braceDepth = 0
        for (index in openingBrace until source.length) {
            when (source[index]) {
                '{' -> braceDepth++
                '}' -> {
                    braceDepth--
                    if (braceDepth == 0) {
                        return source.substring(openingBrace, index + 1)
                    }
                }
            }
        }

        throw AssertionError("Unable to determine the end of createLanHttpClient")
    }
}
