package io.github.stardomains3.oxproxion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Data classes for API requests and responses

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<FlexibleMessage>,
    val usage: UsageRequest? = null,
    val models: List<String>? = null,
    val reasoning: Reasoning? = null,
    val stream: Boolean = false,
    val max_tokens: Int? = null,
    val user: String? = null,
    val tools: List<Tool>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null,
    @SerialName("search_parameters")
    val searchParameters: SearchParameters? = null,
    val temperature: Double? = null
)

@Serializable
data class Message(
    val content: String,
    val role: String
)

@Serializable
data class FlexibleMessage(
    val role: String,
    val content: JsonElement
)

@Serializable
data class UsageRequest(
    val include: Boolean
)

@Serializable
data class Reasoning(
    val enabled: Boolean? = null
)
@Serializable
data class CreditsResponse(
    val data: CreditsData
)

@Serializable
data class CreditsData(
    @SerialName("total_credits")
    val totalCredits: Double,
    @SerialName("total_usage")
    val totalUsage: Double
)


// Response data classes
@Serializable
data class ChatResponse(
    val id: String? = null,
    val provider: String? = null,
    val model: String,
    val `object`: String,
    val created: Long,
    val choices: List<Choice>,
    val usage: UsageResponse? = null,
    val citations: List<String>? = null
)

@Serializable
data class Choice(
    val logprobs: String? = null,
    val finish_reason: String,
    val native_finish_reason: String? = null, // Changed to nullable
    val index: Int,
    val message: MessageResponse
)

@Serializable
data class MessageResponse(
    val content: String,
    val role: String
)

@Serializable
data class UsageResponse(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int,
    val cost: Double? = null,
    val is_byok: Boolean? = null,
    // Change this field to be nullable
    val prompt_tokens_details: PromptTokensDetails? = null,
    val cost_details: CostDetails? = null,
    // Also change this field to be nullable as a precaution
    val completion_tokens_details: CompletionTokensDetails? = null
)

@Serializable
data class PromptTokensDetails(
    val cached_tokens: Int
)

@Serializable
data class CostDetails(
    val upstream_inference_cost: Double? = null
)

@Serializable
data class CompletionTokensDetails(
    val reasoning_tokens: Int
)
@Serializable
data class ImageData(
    val url: String
    // APIs can also return b64_json or revised_prompt, but we only need the URL
)
@Serializable
data class Tool(
    val type: String,
    val function: FunctionTool
)

@Serializable
data class FunctionTool(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String,
    val function: FunctionCall
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String // This is a JSON string
)
@Serializable
data class SearchParameters(
    val mode: String,
    val sources: List<SearchSource>,
    // You could also add other optional fields here like:
    @SerialName("return_citations") val returnCitations: Boolean? = null,
    @SerialName("max_search_results") val maxSearchResults: Int? = null
)

@Serializable
data class SearchSource(
    val type: String
)

@Serializable
data class StreamedChatResponse(
    val id: String,
    val model: String,
    val `object`: String,
    val created: Long,
    val choices: List<StreamedChoice>
)

@Serializable
data class StreamedChoice(
    val index: Int,
    val delta: StreamedDelta,
    val finish_reason: String? = null
)

@Serializable
data class StreamedDelta(
    val role: String? = null,
    val content: String? = null
)