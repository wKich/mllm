package com.mllm.chat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mllm.chat.data.model.Conversation
import com.mllm.chat.data.model.Message

@Database(
    entities = [Conversation::class, Message::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add reasoningContent column to messages table
                db.execSQL("ALTER TABLE messages ADD COLUMN reasoningContent TEXT")
            }
        }
    }
}
