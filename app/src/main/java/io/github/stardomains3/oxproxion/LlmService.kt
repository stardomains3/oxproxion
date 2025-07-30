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
        private const val DEFAULT_TITLE_MODEL = "mistralai/mistral-tiny"
    }

    suspend fun getSuggestedChatTitle(chatContent: String, apiKey: String): String? {
        val prompt = "Respond only with a 1 to 5 word title for a save title for this chat. $chatContent"
        val messages = listOf(
            FlexibleMessage(role = "user", content = JsonPrimitive(prompt))
        )

        try {
            return withTimeout(SUGGESTION_TIMEOUT_MS) {
                val chatRequest = ChatRequest(
                    model = DEFAULT_TITLE_MODEL,
                    messages = messages,
                    max_tokens = 20,
                    temperature = 0.7
                )

                val response = httpClient.post(baseUrl) {
                    header("Authorization", "Bearer $apiKey")
                    contentType(ContentType.Application.Json)
                    setBody(chatRequest)
                }

                if (!response.status.isSuccess()) {
                    val errorBody = try { response.bodyAsText() } catch (ex: Exception) { "No details" }
                    Log.e(TAG, "API Error getting title: ${response.status} - $errorBody")
                    return@withTimeout null
                }

                val chatResponse = response.body<ChatResponse>()
                chatResponse.choices.firstOrNull()?.message?.content?.trim()?.let {
                    it.replace("\"", "").replace("'", "").trim()
                }
            }
        } catch (e: Exception) {
            val errorMsg = when (e) {
                is TimeoutCancellationException, is SocketTimeoutException -> "Request timed out for title suggestion."
                is ClientRequestException -> "Client error ${e.response.status} getting title."
                is ServerResponseException -> "Server error ${e.response.status} getting title."
                is IOException -> "Network error getting title."
                else -> "Unexpected error getting title: ${e.localizedMessage ?: "Unknown"}"
            }
            Log.e(TAG, errorMsg, e)
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