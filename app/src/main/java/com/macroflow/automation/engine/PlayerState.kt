package com.macroflow.automation.engine

enum class PlayerStatus { IDLE, STARTING, PLAYING, PAUSED }

enum class PlayerError {
    NO_ACCESSIBILITY,
    MACRO_NOT_FOUND,
    EMPTY_MACRO,
    INVALID_ACTION,
    GESTURE_FAILED,
    TIME_LIMIT,
    ALREADY_RUNNING,
}

data class PlayerState(
    val status: PlayerStatus = PlayerStatus.IDLE,
    val macroId: Long? = null,
    val macroName: String = "",
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val loop: Int = 0,
    val loops: Int = 0,
    /** True when the macro repeats until the user stops it (loops is then 0). */
    val loopForever: Boolean = false,
    /** Set when the last start was refused or a run stopped because of a problem. */
    val error: PlayerError? = null,
)
