package com.macroflow.automation.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Schema is in place in Milestone 1; the editor and recorder that write these rows arrive in Milestone 2. */
@Entity(
    tableName = "macro_actions",
    foreignKeys = [
        ForeignKey(
            entity = MacroEntity::class,
            parentColumns = ["id"],
            childColumns = ["macroId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("macroId")],
)
data class MacroActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val macroId: Long,
    val position: Int,
    val type: String,
    val x: Float,
    val y: Float,
    val endX: Float,
    val endY: Float,
    val durationMs: Long,
    val delayMs: Long,
    val repeatCount: Int,
    val enabled: Boolean,
)
