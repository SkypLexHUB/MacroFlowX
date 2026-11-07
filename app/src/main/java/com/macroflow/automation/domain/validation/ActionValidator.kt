package com.macroflow.automation.domain.validation

import com.macroflow.automation.domain.model.ActionType
import com.macroflow.automation.domain.model.MacroAction

object ActionValidator {
    const val MAX_DURATION_MS = 60_000L
    const val MAX_WAIT_MS = 60_000L
    const val MAX_REPEAT = 999
    const val MAX_ACTIONS_PER_MACRO = 500

    enum class Error { POSITION_OUT_OF_RANGE, INVALID_DURATION, INVALID_WAIT, INVALID_REPEAT }

    /** Returns null when the action can be played. */
    fun validate(action: MacroAction): Error? {
        if (action.delayMs < 0 || action.delayMs > MAX_WAIT_MS) return Error.INVALID_WAIT
        if (action.repeatCount < 1 || action.repeatCount > MAX_REPEAT) return Error.INVALID_REPEAT
        return when (action.type) {
            ActionType.TAP ->
                if (!inRange(action.x) || !inRange(action.y)) Error.POSITION_OUT_OF_RANGE else null
            ActionType.LONG_PRESS -> when {
                !inRange(action.x) || !inRange(action.y) -> Error.POSITION_OUT_OF_RANGE
                action.durationMs < 100 || action.durationMs > MAX_DURATION_MS -> Error.INVALID_DURATION
                else -> null
            }
            ActionType.SWIPE -> when {
                !inRange(action.x) || !inRange(action.y) ||
                    !inRange(action.endX) || !inRange(action.endY) -> Error.POSITION_OUT_OF_RANGE
                action.durationMs < 50 || action.durationMs > MAX_DURATION_MS -> Error.INVALID_DURATION
                else -> null
            }
            ActionType.DELAY ->
                if (action.durationMs < 1 || action.durationMs > MAX_DURATION_MS) Error.INVALID_DURATION else null
        }
    }

    private fun inRange(v: Float) = !v.isNaN() && v >= 0f && v <= 1f
}
