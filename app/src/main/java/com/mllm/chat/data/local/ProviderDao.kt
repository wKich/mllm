package com.mllm.chat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class ProviderDao {

    @Query("SELECT * FROM providers WHERE id = :id LIMIT 1")
    abstract suspend fun getProviderById(id: String): ProviderEntity?

    @Query("SELECT * FROM providers ORDER BY name ASC")
    abstract suspend fun getAllProviders(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE isActive = 1 LIMIT 1")
    abstract suspend fun getActiveProvider(): ProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertProvider(provider: ProviderEntity)

    @Query("DELETE FROM providers WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query("UPDATE providers SET isActive = 0")
    abstract suspend fun clearActive()

    @Query("UPDATE providers SET isActive = 1 WHERE id = :id")
    abstract suspend fun markActive(id: String)

    @Transaction
    open suspend fun setActiveProvider(id: String) {
        clearActive()
        markActive(id)
    }
}
