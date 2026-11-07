package com.macroflow.automation.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.macroflow.automation.data.local.entity.ExecutionHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionHistoryDao {
    @Query("SELECT * FROM execution_history ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ExecutionHistoryEntity>>

    @Query("SELECT COUNT(*) FROM execution_history")
    fun observeTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM execution_history WHERE status = :status")
    fun observeCountByStatus(status: String): Flow<Int>

    @Insert
    suspend fun insert(entry: ExecutionHistoryEntity): Long

    @Query("DELETE FROM execution_history")
    suspend fun clear()
}
