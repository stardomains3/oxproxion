package io.github.stardomains3.oxproxion

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction

data class SessionWithMessages(
    @Embedded val session: ChatSession,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val messages: List<ChatMessage>
)

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession): Long
    @Query("SELECT MAX(id) FROM chat_sessions") // Assuming your session table is named 'chat_sessions'
    suspend fun getMaxSessionId(): Long?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessage>)

    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): LiveData<List<ChatSession>>
    @Query("""
    SELECT DISTINCT s.id 
    FROM chat_sessions s 
    LEFT JOIN chat_messages m ON s.id = m.sessionId 
    WHERE s.title LIKE :query OR m.content LIKE :query
""")
    suspend fun searchSessionIds(query: String): List<Long>

    @Transaction
    @Query("SELECT * FROM chat_sessions")
    suspend fun getAllSessionsWithMessages(): List<SessionWithMessages>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getMessagesForSession(sessionId: Long): List<ChatMessage>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): ChatSession?

    @Query("UPDATE chat_sessions SET title = :newTitle WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: Long, newTitle: String)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Transaction
    suspend fun insertSessionAndMessages(session: ChatSession, messages: List<ChatMessage>) {
        val sessionId = insertSession(session)
        val messagesWithSessionId = messages.map { it.copy(sessionId = sessionId) }
        insertMessages(messagesWithSessionId)
    }
}
