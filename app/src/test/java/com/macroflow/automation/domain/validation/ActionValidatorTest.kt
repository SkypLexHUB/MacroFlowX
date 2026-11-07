package com.macroflow.automation.domain.validation

import com.macroflow.automation.domain.model.ActionType
import com.macroflow.automation.domain.model.MacroAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionValidatorTest {
    private fun tap(x: Float = 0.5f, y: Float = 0.5f) = MacroAction(type = ActionType.TAP, x = x, y = y)

    @Test
    fun validTap_returnsNull() = assertNull(ActionValidator.validate(tap()))

    @Test
    fun tapOutsideScreen_isRejected() {
        assertEquals(ActionValidator.Error.POSITION_OUT_OF_RANGE, ActionValidator.validate(tap(x = 1.2f)))
        assertEquals(ActionValidator.Error.POSITION_OUT_OF_RANGE, ActionValidator.validate(tap(y = -0.1f)))
        assertEquals(ActionValidator.Error.POSITION_OUT_OF_RANGE, ActionValidator.validate(tap(x = Float.NaN)))
    }

    @Test
    fun repeatMustBeBetween1And999() {
        assertEquals(ActionValidator.Error.INVALID_REPEAT, ActionValidator.validate(tap().copy(repeatCount = 0)))
        assertEquals(ActionValidator.Error.INVALID_REPEAT, ActionValidator.validate(tap().copy(repeatCount = 1000)))
        assertNull(ActionValidator.validate(tap().copy(repeatCount = 999)))
    }

    @Test
    fun negativeWait_isRejected() {
        assertEquals(ActionValidator.Error.INVALID_WAIT, ActionValidator.validate(tap().copy(delayMs = -1)))
    }

    @Test
    fun longPress_needsDuration() {
        val a = MacroAction(type = ActionType.LONG_PRESS, durationMs = 50)
        assertEquals(ActionValidator.Error.INVALID_DURATION, ActionValidator.validate(a))
        assertNull(ActionValidator.validate(a.copy(durationMs = 800)))
    }

    @Test
    fun swipe_needsBothEndsInsideScreen() {
        val a = MacroAction(type = ActionType.SWIPE, durationMs = 300, endY = 2f)
        assertEquals(ActionValidator.Error.POSITION_OUT_OF_RANGE, ActionValidator.validate(a))
        assertNull(ActionValidator.validate(a.copy(endY = 0.2f)))
    }

    @Test
    fun delay_needsPositiveDurationUpToLimit() {
        val a = MacroAction(type = ActionType.DELAY, durationMs = 0)
        assertEquals(ActionValidator.Error.INVALID_DURATION, ActionValidator.validate(a))
        assertEquals(
            ActionValidator.Error.INVALID_DURATION,
            ActionValidator.validate(a.copy(durationMs = ActionValidator.MAX_DURATION_MS + 1)),
        )
        assertNull(ActionValidator.validate(a.copy(durationMs = 1000)))
    }
}
