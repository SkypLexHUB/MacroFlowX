package com.macroflow.automation.ui.player

import androidx.annotation.StringRes
import com.macroflow.automation.R
import com.macroflow.automation.engine.PlayerError

@StringRes
fun PlayerError.messageRes(): Int = when (this) {
    PlayerError.NO_ACCESSIBILITY -> R.string.err_no_acc
    PlayerError.MACRO_NOT_FOUND -> R.string.err_not_found
    PlayerError.EMPTY_MACRO -> R.string.err_empty
    PlayerError.INVALID_ACTION -> R.string.err_invalid
    PlayerError.GESTURE_FAILED -> R.string.err_gesture
    PlayerError.TIME_LIMIT -> R.string.err_time_limit
    PlayerError.ALREADY_RUNNING -> R.string.err_busy
}
