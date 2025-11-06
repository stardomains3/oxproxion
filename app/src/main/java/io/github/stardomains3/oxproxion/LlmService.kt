package io.github.stardomains3.oxproxion

import android.util.Log
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

class LlmService(
    private val httpClient: HttpClient,
    private val baseUrl: String
) {

    companion object {
        private const val SUGGESTION_TIMEOUT_MS = 15_000L
        private const val TAG = "LlmService"
        private const val DEFAULT_TITLE_MODEL = "qwen/qwen3-30b-a3b-instruct-2507"
    }

    suspend fun getSuggestedChatTitle(
        chatContent: String,
        apiKey: String,
        modelId: String,           // NEW: model to use
        endpoint: String,          // NEW: endpoint to call
        isLanModel: Boolean = false // NEW: tells service what auth header to use
    ): String? {
        val prompt = "Respond only with a 1 to 8 word title for a save title for this chat. Do not use Markdown in your response. Chat Contents: ```$chatContent```"

        val messages = listOf(
            FlexibleMessage(role = "user", content = JsonPrimitive(prompt))
        )

        try {
            return withTimeout(SUGGESTION_TIMEOUT_MS) {
                val authHeader = if (isLanModel) {
                    "Bearer any-non-empty-string"
                } else {
                    "Bearer $apiKey"
                }

                val chatRequest = ChatRequest(
                    model = modelId,
                    messages = messages,
                    max_tokens = 100,
                    logprobs = false,
                    temperature = 0.7,
                    stream = false
                )

                val response = httpClient.post(endpoint) {
                    header("Authorization", authHeader)
                    contentType(ContentType.Application.Json)
                    setBody(chatRequest)
                }

                if (!response.status.isSuccess()) {
                    val errorBody = try { response.bodyAsText() } catch (ex: Exception) { "No details available" }
                    val errorMessage = "Error: API request failed with status ${response.status.value} - $errorBody"
                    return@withTimeout errorMessage
                }


                val chatResponse = response.body<ChatResponse>()
                val message = chatResponse.choices.firstOrNull()?.message ?: return@withTimeout "Untitled Chat"
                val content = message.content ?: "Untitled Chat"

                val trimmed = content.trim()
                if (trimmed.isBlank()) {
                    return@withTimeout "Untitled Chat"
                }

                trimmed
            }
        } catch (e: Exception) {
            val errorMsg = when (e) {
                is TimeoutCancellationException, is SocketTimeoutException -> "Request timed out for title suggestion."
                is ClientRequestException -> "Client error ${e.response.status} getting title."
                is ServerResponseException -> "Server error ${e.response.status} getting title."
                is IOException -> "Network error getting title."
                else -> "Unexpected error getting title: ${e.localizedMessage ?: "Unknown"}"
            }
            return null
        }
    }

    suspend fun getRemainingCredits(apiKey: String): Double? {
        if (apiKey.isBlank()) {
            Log.e(TAG, "API key is blank. Cannot fetch credits.")
            return null
        }
        try {
            val response = httpClient.get("https://openrouter.ai/api/v1/credits") {
                header("Authorization", "Bearer $apiKey")
            }

            if (!response.status.isSuccess()) {
                val errorBody = try { response.bodyAsText() } catch (ex: Exception) { "No details" }
                Log.e(TAG, "API Error getting credits: ${response.status} - $errorBody")
                return null
            }

            val creditsResponse = response.body<CreditsResponse>()
            return creditsResponse.data.totalCredits - creditsResponse.data.totalUsage
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch credits", e)
            return null
        }
    }
}