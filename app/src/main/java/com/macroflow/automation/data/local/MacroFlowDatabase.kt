package com.macroflow.automation.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.macroflow.automation.data.local.dao.ExecutionHistoryDao
import com.macroflow.automation.data.local.dao.MacroActionDao
import com.macroflow.automation.data.local.dao.MacroDao
import com.macroflow.automation.data.local.entity.ExecutionHistoryEntity
import com.macroflow.automation.data.local.entity.MacroActionEntity
import com.macroflow.automation.data.local.entity.MacroEntity

@Database(
    entities = [MacroEntity::class, MacroActionEntity::class, ExecutionHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MacroFlowDatabase : RoomDatabase() {
    abstract fun macroDao(): MacroDao
    abstract fun macroActionDao(): MacroActionDao
    abstract fun executionHistoryDao(): ExecutionHistoryDao
}
