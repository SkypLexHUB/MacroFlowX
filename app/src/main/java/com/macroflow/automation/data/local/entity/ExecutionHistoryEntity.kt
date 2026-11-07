package com.macroflow.automation.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** No foreign key on purpose: history must survive deleting the macro it came from. */
@Entity(tableName = "execution_history")
data class ExecutionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val macroId: Long?,
    val macroName: String,
    val startedAt: Long,
    val durationMs: Long,
    val actionCount: Int,
    val status: String,
    val failureReason: String?,
)
