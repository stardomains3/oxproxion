package io.github.stardomains3.oxproxion

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit


class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    private val json = Json { ignoreUnknownKeys = true }
    private val _sharedText = MutableStateFlow<String?>(null)
    val sharedText: StateFlow<String?> = _sharedText
    fun isVisionModel(modelIdentifier: String?): Boolean {
        if (modelIdentifier == null) return false

        val customModels = sharedPreferencesHelper.getCustomModels()
        val allModels = getBuiltInModels() + customModels

        val model = allModels.find { it.apiIdentifier == modelIdentifier }
        return model?.isVisionCapable ?: false
    }

    fun hasImagesInChat(): Boolean = _chatMessages.value?.any { isImageMessage(it) } ?: false

    private fun isImageMessage(message: FlexibleMessage): Boolean =
        (message.content as? JsonArray)?.any { item ->
            (item as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "image_url"
        } ?: false

    fun getMessageText(content: JsonElement): String {
        if (content is JsonPrimitive) return content.content
        if (content is JsonArray) {
            return content.firstNotNullOfOrNull { item ->
                (item as? JsonObject)?.takeIf { it["type"]?.jsonPrimitive?.content == "text" }?.get("text")?.jsonPrimitive?.content
            } ?: ""
        }
        return ""
    }

    private val repository: ChatRepository
    private var currentSessionId: Long? = null

    // State Management
    private val _chatMessages = MutableLiveData<List<FlexibleMessage>>(emptyList())
    val chatMessages: LiveData<List<FlexibleMessage>> = _chatMessages
    private val _activeChatModel = MutableLiveData<String>()
    val activeChatModel: LiveData<String> = _activeChatModel
    private val _isAwaitingResponse = MutableLiveData<Boolean>(false)
    val isAwaitingResponse: LiveData<Boolean> = _isAwaitingResponse
    private val _modelPreferenceToSave = MutableLiveData<String?>()
    val modelPreferenceToSave: LiveData<String?> = _modelPreferenceToSave
    private val _creditsResult = MutableLiveData<Event<String>>()
    val creditsResult: LiveData<Event<String>> = _creditsResult
    private val _isStreamingEnabled = MutableLiveData<Boolean>(false)
    val isStreamingEnabled: LiveData<Boolean> = _isStreamingEnabled
    private val _isSoundEnabled = MutableLiveData<Boolean>(false)
    val isSoundEnabled: LiveData<Boolean> = _isSoundEnabled
    private val _scrollToBottomEvent = MutableLiveData<Event<Unit>>()
    val scrollToBottomEvent: LiveData<Event<Unit>> = _scrollToBottomEvent
    private var networkJob: Job? = null


    fun toggleStreaming() {
        val newStremingState = !(_isStreamingEnabled.value ?: false)
        _isStreamingEnabled.value = newStremingState
        sharedPreferencesHelper.saveStreamingPreference(newStremingState)
    }

    fun toggleSound() {
        val newSoundState = !(_isSoundEnabled.value ?: false)
        _isSoundEnabled.value = newSoundState
        sharedPreferencesHelper.saveSoundPreference(newSoundState)
    }

    var activeChatUrl: String = "https://openrouter.ai/api/v1/chat/completions"
    var activeChatApiKey: String = ""
    // var runningCost: Double = 0.0 // Updated on successful responses

    companion object {
        private const val TIMEOUT_MS = 150_000L
        val THINKING_MESSAGE = FlexibleMessage(
            role = "assistant",
            content = JsonPrimitive("thinking...")
        )
    }

    private val httpClient: HttpClient
    private var llmService: LlmService
    private val sharedPreferencesHelper: SharedPreferencesHelper
    private val soundManager: SoundManager

    init {
        sharedPreferencesHelper = SharedPreferencesHelper(application)
        soundManager = SoundManager(application)
        val chatDao = AppDatabase.getDatabase(application).chatDao()
        repository = ChatRepository(chatDao)
        httpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            engine {
                config {
                    connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                }
            }
        }
        _activeChatModel.value = sharedPreferencesHelper.getPreferenceModelnew()
        _isStreamingEnabled.value = sharedPreferencesHelper.getStreamingPreference()
        _isSoundEnabled.value = sharedPreferencesHelper.getSoundPreference()
        llmService = LlmService(httpClient, activeChatUrl)
        activeChatApiKey = sharedPreferencesHelper.getApiKeyFromPrefs("openrouter_api_key")
    }

    override fun onCleared() {
        super.onCleared()
        soundManager.release()
        httpClient.close() // Prevent leaks
    }

    fun playCancelTone() {
        soundManager.playCancelTone()
    }

    fun setModel(model: String) {
        _activeChatModel.value = model
        _modelPreferenceToSave.value = model
    }

    fun saveCurrentChat(title: String) {
        viewModelScope.launch {
            val sessionId = currentSessionId ?: repository.getNextSessionId() // Generate if new
            val session = ChatSession(
                id = sessionId,
                title = title,
                modelUsed = _activeChatModel.value ?: ""
            )
            val messages = _chatMessages.value?.map {
                ChatMessage(
                    sessionId = sessionId,
                    role = it.role,
                    content = json.encodeToString(JsonElement.serializer(), it.content)
                )
            } ?: emptyList()
            repository.insertSessionAndMessages(session, messages)
            currentSessionId = sessionId // Update after save
        }
    }

    fun loadChat(sessionId: Long) {
        viewModelScope.launch {
            // Parallel fetch for efficiency
            val sessionDeferred = async { repository.getSessionById(sessionId) }
            val messagesDeferred = async { repository.getMessagesForSession(sessionId) }

            val session = sessionDeferred.await()
            val messages = messagesDeferred.await()

            _chatMessages.postValue(messages.map {
                FlexibleMessage(
                    role = it.role,
                    content = try {
                        json.parseToJsonElement(it.content)
                    } catch (e: Exception) {
                        JsonPrimitive(it.content)
                    }
                )
            })
            currentSessionId = sessionId

            session?.let {
                _activeChatModel.postValue(it.modelUsed)
                _modelPreferenceToSave.postValue(it.modelUsed)
            }
        }
    }

    fun onModelPreferenceSaved() {
        _modelPreferenceToSave.value = null
    }

    fun getFormattedChatHistory(): String {
        return _chatMessages.value?.mapNotNull { message ->
            val contentText = getMessageText(message.content).trim()
            if (contentText.isEmpty() || contentText == "thinking...") null
            else when (message.role) {
                "user" -> "User: $contentText"
                "assistant" -> "AI: $contentText"
                else -> null
            }
        }?.joinToString("\n\n") ?: ""
    }

    fun cancelCurrentRequest() {
        networkJob?.cancel()
    }

    fun sendUserMessage(
        userContent: JsonElement,
        systemMessage: String? = null
    ) {
        val userMessage = FlexibleMessage(role = "user", content = userContent)
        val thinkingMessage = THINKING_MESSAGE

        val messagesForApiRequest = mutableListOf<FlexibleMessage>()

        if (systemMessage != null) {
            messagesForApiRequest.add(FlexibleMessage(role = "system", content = JsonPrimitive(systemMessage)))
        }

        _chatMessages.value?.let { history ->
            messagesForApiRequest.addAll(history)
        }

        messagesForApiRequest.add(userMessage)

        val uiMessages = _chatMessages.value?.toMutableList() ?: mutableListOf()
        uiMessages.add(userMessage)
        uiMessages.add(thinkingMessage)

        _chatMessages.value = uiMessages
        _isAwaitingResponse.value = true

        networkJob = viewModelScope.launch {
            try {
                val modelForRequest = _activeChatModel.value ?: throw IllegalStateException("No active chat model")
                if (_isStreamingEnabled.value == true) {
                    handleStreamedResponse(modelForRequest, messagesForApiRequest, thinkingMessage)
                } else {
                    handleNonStreamedResponse(modelForRequest, messagesForApiRequest, thinkingMessage)
                }
            } catch (e: Throwable) {
                handleError(e, thinkingMessage)
            } finally {
                _isAwaitingResponse.postValue(false)
                _scrollToBottomEvent.postValue(Event(Unit))
                networkJob = null
            }
        }
    }

    private suspend fun handleStreamedResponse(modelForRequest: String, messagesForApiRequest: List<FlexibleMessage>, thinkingMessage: FlexibleMessage) {
        withContext(Dispatchers.IO) {
            val chatRequest = ChatRequest(
                model = modelForRequest,
                messages = messagesForApiRequest,
                stream = true,
                max_tokens = 2800
            )

            try {
                httpClient.preparePost(activeChatUrl) {
                    header("Authorization", "Bearer $activeChatApiKey")
                    header("HTTP-Referer", "https://github.com/stardomains3/oxproxion/")
                    header("X-Title", "oxproxion")
                    contentType(ContentType.Application.Json)
                    setBody(chatRequest)
                }.execute { httpResponse ->
                    if (!httpResponse.status.isSuccess()) {
                        val errorBody = try { httpResponse.bodyAsText() } catch (ex: Exception) { "No details" }
                        throw Exception("API Error: ${httpResponse.status} - $errorBody")
                    }

                    val channel = httpResponse.body<ByteReadChannel>()
                    var accumulatedResponse = ""

                    withContext(Dispatchers.Main) {
                        updateMessages {
                            val index = it.indexOf(thinkingMessage)
                            if (index != -1) {
                                it[index] = FlexibleMessage(role = "assistant", content = JsonPrimitive(""))
                            }
                        }
                    }

                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: continue
                        if (line.startsWith("data:")) {
                            val jsonString = line.substring(5).trim()
                            if (jsonString == "[DONE]") break

                            try {
                                val chunk = json.decodeFromString<StreamedChatResponse>(jsonString)
                                chunk.choices.firstOrNull()?.delta?.content?.let { content ->
                                    accumulatedResponse += content
                                    withContext(Dispatchers.Main) {
                                        updateMessages {
                                            val lastMessage = it.last()
                                            it[it.size - 1] = lastMessage.copy(content = JsonPrimitive(accumulatedResponse))
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("ChatViewModel", "Error parsing stream chunk: $jsonString", e)
                            }
                        }
                    }
                }
                soundManager.playSuccessTone()
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    handleError(e, thinkingMessage)
                }
            }
        }
    }

    private suspend fun handleNonStreamedResponse(modelForRequest: String, messagesForApiRequest: List<FlexibleMessage>, thinkingMessage: FlexibleMessage) {
        withTimeout(TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                val chatRequest = ChatRequest(
                    model = modelForRequest,
                    messages = messagesForApiRequest,
                    //  usage = UsageRequest(include = true),
                    max_tokens = 2800,
                )

                val response = httpClient.post(activeChatUrl) {
                    header("Authorization", "Bearer $activeChatApiKey")
                    header("HTTP-Referer", "https://github.com/stardomains3/oxproxion/")
                    header("X-Title", "oxproxion")
                    contentType(ContentType.Application.Json)
                    setBody(chatRequest)
                }

                if (!response.status.isSuccess()) {
                    val errorBody = try { response.bodyAsText() } catch (ex: Exception) { "No details" }
                    throw Exception("API Error: ${response.status} - $errorBody")
                }

                response.body<ChatResponse>()
            }.let { chatResponse ->
                handleSuccessResponse(chatResponse, thinkingMessage)
            }
        }
    }

    private fun handleSuccessResponse(
        chatResponse: ChatResponse,
        thinkingMessage: FlexibleMessage
    ) {
        val responseText = chatResponse.choices.firstOrNull()?.message?.content ?: "No response received."
        val finalAiMessage = FlexibleMessage(role = "assistant", content = JsonPrimitive(responseText))

        updateMessages { list ->
            val index = list.indexOf(thinkingMessage)
            if (index != -1) list[index] = finalAiMessage
        }
        soundManager.playSuccessTone()
    }

    private fun handleError(e: Throwable, thinkingMessage: FlexibleMessage) {
        soundManager.playErrorTone()
        if (e is CancellationException && e !is TimeoutCancellationException) {
            Log.w("ChatNetwork", "Request cancelled by user.", e)
            updateMessages { list ->
                val thinkingIndex = list.indexOf(thinkingMessage)
                if (thinkingIndex != -1) {
                    // Not streaming yet, replace "thinking..." with "Request cancelled."
                    list[thinkingIndex] = FlexibleMessage(role = "assistant", content = JsonPrimitive("Request cancelled."))
                } else if (list.lastOrNull()?.role == "assistant") {
                    // Streaming, append to partial response.
                    val lastMessage = list.last()
                    val currentText = getMessageText(lastMessage.content)
                    if (!currentText.contains("[Cancelled]", ignoreCase = true)) {
                        val newText = if (currentText.isEmpty()) "Request cancelled." else "$currentText\n\n[Cancelled]"
                        list[list.size - 1] = lastMessage.copy(content = JsonPrimitive(newText))
                    }
                }
            }
            return
        }

        Log.e("ChatNetwork", "Request failed.".replace("\\n", "\n"), e)
        val errorMsg = when (e) {
            is TimeoutCancellationException, is SocketTimeoutException -> "**Error:** Request timed out after 90 seconds. Please try again."
            is ClientRequestException -> "Client error: ${e.response.status}. Check your input."
            is ServerResponseException -> "Server error: ${e.response.status}. Try later."
            is IOException -> "Network error: Check your connection."
            else -> "Unexpected error: ${e.localizedMessage ?: "Unknown"}"
        }

        val errorMessage = FlexibleMessage(role = "assistant", content = JsonPrimitive(errorMsg))
        updateMessages { list ->
            val index = list.indexOf(thinkingMessage)
            if (index != -1) list[index] = errorMessage
        }
    }

    private fun updateMessages(updateBlock: (MutableList<FlexibleMessage>) -> Unit) {
        val current = _chatMessages.value?.toMutableList() ?: mutableListOf()
        updateBlock(current)
        _chatMessages.value = current
    }

    fun startNewChat() {
        _chatMessages.value = emptyList()
        //  runningCost = 0.0
        currentSessionId = null
    }

    fun hasWebpInHistory(): Boolean {
        val messages = _chatMessages.value ?: return false
        return messages.any {
            val contentArray = it.content as? kotlinx.serialization.json.JsonArray
            contentArray?.any { element ->
                val imageUrl = element.jsonObject["image_url"]?.jsonObject?.get("url")?.jsonPrimitive?.content
                imageUrl?.startsWith("data:image/webp") == true
            } == true
        }
    }
    fun checkRemainingCredits() {
        viewModelScope.launch {
            val remaining = withContext(Dispatchers.IO) {
                llmService.getRemainingCredits(activeChatApiKey)
            }
            if (remaining != null) {
                val formattedCredits = String.format("%.4f", remaining)
                _creditsResult.postValue(Event("Remaining Credits: $formattedCredits"))
            } else {
                _creditsResult.postValue(Event("Failed to retrieve credits."))
            }
        }
    }
    fun refreshApiKey() {
        activeChatApiKey = sharedPreferencesHelper.getApiKeyFromPrefs("openrouter_api_key")
    }
    fun supportsWebp(modelName: String): Boolean {
        return !modelName.lowercase().contains("grok")
    }
    suspend fun getSuggestedChatTitle(): String? {
        val chatContent = getFormattedChatHistory()
        return llmService.getSuggestedChatTitle(chatContent, activeChatApiKey)

    }

    private fun getBuiltInModels(): List<LlmModel> {
        return listOf(
            LlmModel("Llama 4 Maverick", "meta-llama/llama-4-maverick", true)
        )
    }

    fun getModelDisplayName(apiIdentifier: String): String {
        val builtInModels = getBuiltInModels()
        val customModels = sharedPreferencesHelper.getCustomModels()
        val allModels = builtInModels + customModels
        return allModels.find { it.apiIdentifier == apiIdentifier }?.displayName ?: apiIdentifier
    }
    fun consumeSharedText(text: String) {
        _sharedText.value = text
    }

    fun textConsumed() {
        _sharedText.value = null
    }
}