package io.github.stardomains3.oxproxion

import android.util.Log
import io.github.stardomains3.oxproxion.SharedPreferencesHelper.Companion.LAN_PROVIDER_LLAMA_CPP
import io.github.stardomains3.oxproxion.SharedPreferencesHelper.Companion.LAN_PROVIDER_MLX_LM
import io.github.stardomains3.oxproxion.SharedPreferencesHelper.Companion.LAN_PROVIDER_NATIV
import io.github.stardomains3.oxproxion.SharedPreferencesHelper.Companion.LAN_PROVIDER_OLLAMA
import io.github.stardomains3.oxproxion.SharedPreferencesHelper.Companion.LAN_PROVIDER_OMLX
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.time.Duration.Companion.milliseconds


class LlmService(
    private val httpClient: HttpClient,
    private val baseUrl: String
) {

    companion object {
        private const val SUGGESTION_TIMEOUT_MS = 15_000L
        private const val TAG = "LlmService"
        private const val DEFAULT_TITLE_MODEL = "google/gemma-4-26b-a4b-it"

        // Hard cap so a rambling model can't produce a giant title
        private const val MAX_TITLE_CHARS = 80
    }

    suspend fun getSuggestedChatTitle(
        chatContent: String,
        apiKey: String,
        modelId: String,
        endpoint: String,
        isLanModel: Boolean = false,
        lanProvider: String? = null,
        isReasoningModel: Boolean = false,
        isThinkingEnabled: Boolean = false,
        client: HttpClient? = null
    ): String? {
        // TRUNCATION-AWARE PROMPT: the ViewModel now sends a head+tail EXCERPT
        // (opening + latest messages, per-message truncation, hard char cap),
        // not the full chat. The prompt must say so, or models hedge with vague
        // titles like "Discussion About Various Topics" when they notice the
        // abrupt cut.
        //
        // NOTE: no ``` fencing — chat content itself often contains markdown
        // code fences (this is a coding chat app), which would break/mangle
        // the fence. Explicit start/end markers are unambiguous instead.
        val prompt = buildString {
            append("Respond with ONLY a 1-8 word title summarizing the topic of this conversation excerpt. ")
            append("No quotes, no Markdown, no explanation — just the title.\n\n")
            append("The excerpt shows the opening and most recent messages of the chat. ")
            append("It may be truncated or partial — infer the overall topic from what is present.\n\n")
            append("Conversation excerpt:\n")
            append(chatContent)
            append("\nEnd of excerpt.")
        }

        val messages = listOf(
            FlexibleMessage(role = "user", content = JsonPrimitive(prompt))
        )

        // Ollama: "think" is NOT supported on /v1/chat/completions (Responses API only).
        // reasoning_effort "none" is the correct way to disable thinking there.
        val reasoningEffortParam = if (lanProvider == LAN_PROVIDER_OLLAMA && isReasoningModel) {
            "none"
        } else {
            null
        }
        val llamaCppKwargs = if (isReasoningModel && (
                    lanProvider == LAN_PROVIDER_LLAMA_CPP ||
                            lanProvider == LAN_PROVIDER_OMLX ||
                            lanProvider == LAN_PROVIDER_MLX_LM
                    )) {
            mapOf("enable_thinking" to JsonPrimitive(false))
        } else null
        // Nativ (mlx-vlm) — top-level toggle
        val nativThinkingParam = if (lanProvider == LAN_PROVIDER_NATIV && isReasoningModel) {
            false
        } else null

        try {
            return withTimeout(SUGGESTION_TIMEOUT_MS.milliseconds) {
                val authHeader = "Bearer $apiKey"

                val chatRequest = ChatRequest(
                    model = modelId,
                    messages = messages,
                    max_tokens = 40,
                    temperature = 0.1,
                    stream = false,
                    reasoningEffort = reasoningEffortParam, // Ollama: "none" disables thinking
                    enableThinking = nativThinkingParam,     // Nativ
                    chatTemplateKwargs = llamaCppKwargs      // llama.cpp / oMLX / mlx_lm
                )
                // USE THE PROVIDED CLIENT, OR FALLBACK TO THE DEFAULT ONE
                val activeClient = client ?: httpClient

                val response = activeClient.post(endpoint) {
                    header("Authorization", authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(chatRequest)
                }

                if (!response.status.isSuccess()) {
                    val errorBody = try { response.bodyAsText() } catch (ex: Exception) { "No details available" }
                    // FIX: previously the error text was returned AS THE TITLE —
                    // chats could literally get named "Error: API request failed
                    // with status 401 - ...". Log it and return null so the
                    // caller applies its own fallback title.
                   // Log.w(TAG, "Title suggestion failed: ${response.status.value} - $errorBody")
                    return@withTimeout null
                }

                val chatResponse = response.body<ChatResponse>()
                val message = chatResponse.choices.firstOrNull()?.message ?: return@withTimeout "Untitled Chat"
                val content = message.content ?: return@withTimeout "Untitled Chat"

                // Light sanitization: models love wrapping titles in quotes
                val trimmed = content.trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
                    .trim()

                if (trimmed.isBlank()) return@withTimeout "Untitled Chat"

                // Hard cap so a rambling model can't create a giant title
                if (trimmed.length > MAX_TITLE_CHARS) {
                    trimmed.take(MAX_TITLE_CHARS).trimEnd()
                } else {
                    trimmed
                }
            }
        } catch (e: Exception) {
            val errorMsg = when (e) {
                is TimeoutCancellationException, is SocketTimeoutException -> "Title suggestion timed out."
                is ClientRequestException -> "Title suggestion client error ${e.response.status.value}."
                is ServerResponseException -> "Title suggestion server error ${e.response.status.value}."
                is IOException -> "Title suggestion network error."
                else -> "Title suggestion error: ${e.localizedMessage ?: "Unknown"}"
            }
          //  Log.w(TAG, errorMsg)
            return null
        }
    }


    suspend fun getRemainingCredits(apiKey: String): Double? {
        if (apiKey.isBlank()) {
           // Log.e(TAG, "API key is blank. Cannot fetch credits.")
            return null
        }
        try {
            val response = httpClient.get("https://openrouter.ai/api/v1/credits") {
                header("Authorization", "Bearer $apiKey")
            }

            if (!response.status.isSuccess()) {
                val errorBody = try { response.bodyAsText() } catch (ex: Exception) { "No details" }
              //  Log.e(TAG, "API Error getting credits: ${response.status} - $errorBody")
                return null
            }

            val creditsResponse = response.body<CreditsResponse>()
            return creditsResponse.data.totalCredits - creditsResponse.data.totalUsage
        } catch (e: Exception) {
           // Log.e(TAG, "Failed to fetch credits", e)
            return null
        }
    }
}
