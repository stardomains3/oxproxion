package io.github.stardomains3.oxproxion

import androidx.lifecycle.LiveData

class ChatRepository(private val chatDao: ChatDao) {

    val allSessions: LiveData<List<ChatSession>> = chatDao.getAllSessions()

    suspend fun getMessagesForSession(sessionId: Long): List<ChatMessage> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun getSessionById(sessionId: Long): ChatSession? {
        return chatDao.getSessionById(sessionId)
    }

    suspend fun insertSessionAndMessages(session: ChatSession, messages: List<ChatMessage>) {
        chatDao.insertSessionAndMessages(session, messages)
    }

    suspend fun updateSessionTitle(sessionId: Long, newTitle: String) {
        chatDao.updateSessionTitle(sessionId, newTitle)
    }

    suspend fun deleteSession(sessionId: Long) {
        chatDao.deleteSession(sessionId)
    }

    // New method to get the next available session ID (max + 1, or 1 if none)
    suspend fun getNextSessionId(): Long {
        val maxId = chatDao.getMaxSessionId() ?: 0L
        return maxId + 1
    }

    suspend fun getAllSessionsWithMessages(): List<SessionWithMessages> {
        return chatDao.getAllSessionsWithMessages()
    }
    suspend fun searchSessions(query: String): List<ChatSession> {
        val sessionIds = chatDao.searchSessionIds("%$query%")
        return sessionIds.mapNotNull { chatDao.getSessionById(it) }
    }
}