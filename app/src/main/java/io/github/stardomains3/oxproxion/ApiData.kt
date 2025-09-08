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
    val temperature: Double? = null,
    val modalities: List<String>? = null,
    val logprobs: Boolean? = null
)

@Serializable
data class Message(
    val role: String,
    val content: String?,
    val toolCalls: List<ToolCall>? = null,
    val annotations: List<Annotation>? = null
)


@Serializable
data class FlexibleMessage(
    val role: String,
    val content: JsonElement,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
    val toolsUsed: Boolean = false  // NEW: Flag for visual indication (true if tools were involved in this
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
    val logprobs: Logprobs? = null,
    val finish_reason: String? = null,
    val native_finish_reason: String? = null, // Changed to nullable
    val index: Int,
    val message: MessageResponse,
    val error: ErrorResponse? = null
)
@Serializable
data class ErrorResponse(  // <-- ADD THIS: New data class
    val code: Int,
    val message: String,
    val metadata: Map<String, JsonElement>? = null
)
@Serializable
data class Logprobs(
    val content: List<JsonElement>? = null,
    val refusal: List<JsonElement>? = null
)

@Serializable
data class MessageResponse(
    val content: String? = null,
    val role: String,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCall>? = null,
    val annotations: List<Annotation>? = null,
    val images: List<ImageResponse>? = null
)

@Serializable
data class ImageResponse(
    val type: String,  // e.g., "image_url"
    @SerialName("image_url")
    val image_url: ImageUrlResponse
)
@Serializable
data class ImageUrlResponse(
    val url: String  // The base64 data URL, e.g., "data:image/png;base64,..."
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
data class ToolCallChunk(
    val index: Int,
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCallChunk? = null
)

@Serializable
data class FunctionCallChunk(
    val name: String? = null,
    val arguments: String? = null
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
    val finish_reason: String? = null,
    val error: ErrorResponse? = null
)

@Serializable
data class StreamedDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCallChunk>? = null,
    val annotations: List<Annotation>? = null,
    val images: List<io.github.stardomains3.oxproxion.ImageResponse>? = null
)

// NEW: Added from my code for citations
@Serializable
data class Annotation(
    val type: String,
    val url_citation: UrlCitation? = null
)

// NEW: Added from my code for citations
@Serializable
data class UrlCitation(
    val url: String,
    val title: String,
    val content: String? = null,
    val start_index: Int,
    val end_index: Int
)
@Serializable
data class ModerationErrorMetadata(
    val reasons: List<String>,
    val flagged_input: String,
    val provider_name: String,
    val model_slug: String
)

@Serializable
data class OpenRouterError(
    val code: Int,
    val message: String,
    val metadata: JsonObject? = null
)

@Serializable
data class OpenRouterErrorResponse(
    val error: OpenRouterError
)
