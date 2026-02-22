package com.mllm.chat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mllm.chat.data.model.Conversation
import com.mllm.chat.data.model.Message

@Database(
    entities = [Conversation::class, Message::class, ProviderEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun providerDao(): ProviderDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add reasoningContent column to messages table
                db.execSQL("ALTER TABLE messages ADD COLUMN reasoningContent TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS providers (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        baseUrl TEXT NOT NULL,
                        apiKey TEXT NOT NULL,
                        selectedModel TEXT NOT NULL,
                        systemPrompt TEXT NOT NULL,
                        temperature REAL,
                        maxTokens INTEGER,
                        availableModelsJson TEXT NOT NULL,
                        lastFetchedModels INTEGER NOT NULL,
                        isActive INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
