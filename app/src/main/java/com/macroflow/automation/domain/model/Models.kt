package com.macroflow.automation.domain.model

data class Macro(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val lastRunAt: Long? = null,
    val actionCount: Int = 0,
)

enum class ActionType {
    TAP, LONG_PRESS, SWIPE, DELAY;

    companion object {
        fun fromName(name: String?): ActionType =
            values().firstOrNull { it.name == name } ?: TAP
    }
}

/** Coordinates are normalized (0.0 - 1.0) so a macro survives different screen sizes. */
data class MacroAction(
    val id: Long = 0,
    val position: Int = 0,
    val type: ActionType,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val endX: Float = 0.5f,
    val endY: Float = 0.5f,
    val durationMs: Long = 0,
    val delayMs: Long = 0,
    val repeatCount: Int = 1,
    val enabled: Boolean = true,
)

enum class ExecutionStatus {
    SUCCESS, FAILED, STOPPED;

    companion object {
        fun fromName(name: String?): ExecutionStatus =
            values().firstOrNull { it.name == name } ?: FAILED
    }
}

data class ExecutionRecord(
    val id: Long,
    val macroId: Long?,
    val macroName: String,
    val startedAt: Long,
    val durationMs: Long,
    val actionCount: Int,
    val status: ExecutionStatus,
    val failureReason: String?,
)

enum class ThemeMode {
    DARK, LIGHT, SYSTEM;

    companion object {
        fun fromName(name: String?): ThemeMode =
            values().firstOrNull { it.name == name } ?: DARK
    }
}
