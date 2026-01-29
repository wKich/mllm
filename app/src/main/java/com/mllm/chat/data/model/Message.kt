package com.mllm.chat.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val role: String, // "user", "assistant", "system"
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)

enum class MessageRole(val value: String) {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system")
}
