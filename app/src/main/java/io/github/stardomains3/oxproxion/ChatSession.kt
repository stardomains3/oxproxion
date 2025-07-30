package io.github.stardomains3.oxproxion

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val modelUsed: String,
    val timestamp: Long = System.currentTimeMillis()
)
