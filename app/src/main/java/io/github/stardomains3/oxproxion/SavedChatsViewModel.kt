package io.github.stardomains3.oxproxion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class SavedChatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ChatRepository
    val allSessions: LiveData<List<ChatSession>>

    init {
        val chatDao = AppDatabase.getDatabase(application).chatDao()
        repository = ChatRepository(chatDao)
        allSessions = repository.allSessions
    }

    fun deleteSession(sessionId: Long) = viewModelScope.launch {
        repository.deleteSession(sessionId)
    }

    fun updateSessionTitle(sessionId: Long, newTitle: String) = viewModelScope.launch {
        repository.updateSessionTitle(sessionId, newTitle)
    }

    suspend fun getChatsAsJson(): String {
        val sessionsWithMessages = repository.getAllSessionsWithMessages()
        val exportedSessions = sessionsWithMessages.map { sessionWithMessages ->
            ExportedChatSession(
                title = sessionWithMessages.session.title,
                modelUsed = sessionWithMessages.session.modelUsed,
                messages = sessionWithMessages.messages.map { message ->
                    ExportedChatMessage(
                        role = message.role,
                        content = message.content
                    )
                }
            )
        }
        val backup = ChatBackup(sessions = exportedSessions)
        return Json { prettyPrint = true }.encodeToString(backup)
    }

    fun importChatsFromJson(json: String) {
        viewModelScope.launch {
            val backup = Json.decodeFromString<ChatBackup>(json)
            for (exportedSession in backup.sessions) {
                val session = ChatSession(
                    title = exportedSession.title,
                    modelUsed = exportedSession.modelUsed
                )
                val messages = exportedSession.messages.map { exportedMessage ->
                    ChatMessage(
                        sessionId = 0, // This will be overridden by the DAO
                        role = exportedMessage.role,
                        content = exportedMessage.content
                    )
                }
                repository.insertSessionAndMessages(session, messages)
            }
        }
    }
}
