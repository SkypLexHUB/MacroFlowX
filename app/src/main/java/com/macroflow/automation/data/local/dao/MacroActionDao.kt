package com.macroflow.automation.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.macroflow.automation.data.local.entity.MacroActionEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MacroActionDao {
    @Query("SELECT * FROM macro_actions WHERE macroId = :macroId ORDER BY position ASC")
    abstract fun observeForMacro(macroId: Long): Flow<List<MacroActionEntity>>

    @Query("SELECT * FROM macro_actions WHERE macroId = :macroId ORDER BY position ASC")
    abstract suspend fun getForMacro(macroId: Long): List<MacroActionEntity>

    @Insert
    abstract suspend fun insertAll(actions: List<MacroActionEntity>)

    @Query("DELETE FROM macro_actions WHERE macroId = :macroId")
    abstract suspend fun deleteForMacro(macroId: Long)

    /** Atomically replaces all actions of a macro. */
    @Transaction
    open suspend fun replaceForMacro(macroId: Long, actions: List<MacroActionEntity>) {
        deleteForMacro(macroId)
        insertAll(actions)
    }
}
