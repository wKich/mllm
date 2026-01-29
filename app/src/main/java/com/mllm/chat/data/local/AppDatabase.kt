package com.mllm.chat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mllm.chat.data.model.Conversation
import com.mllm.chat.data.model.Message

@Database(
    entities = [Conversation::class, Message::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
}
