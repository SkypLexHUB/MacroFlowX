package com.macroflow.automation.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.macroflow.automation.data.local.entity.MacroEntity
import kotlinx.coroutines.flow.Flow

data class MacroWithCount(
    @Embedded val macro: MacroEntity,
    val actionCount: Int,
)

@Dao
interface MacroDao {
    @Query(
        "SELECT m.*, (SELECT COUNT(*) FROM macro_actions WHERE macroId = m.id) AS actionCount " +
            "FROM macros AS m ORDER BY m.updatedAt DESC",
    )
    fun observeAll(): Flow<List<MacroWithCount>>

    @Query("SELECT * FROM macros WHERE id = :id")
    suspend fun getById(id: Long): MacroEntity?

    @Insert
    suspend fun insert(macro: MacroEntity): Long

    @Update
    suspend fun update(macro: MacroEntity)

    @Query("UPDATE macros SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long)

    @Query("UPDATE macros SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("UPDATE macros SET lastRunAt = :time WHERE id = :id")
    suspend fun setLastRun(id: Long, time: Long)

    @Query("DELETE FROM macros WHERE id = :id")
    suspend fun deleteById(id: Long)
}
