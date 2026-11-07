package com.macroflow.automation.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MacroValidatorTest {
    @Test
    fun validInput_returnsNull() {
        assertNull(MacroValidator.validate("Daily login", "Opens the app and taps login"))
    }

    @Test
    fun blankName_isRejected() {
        assertEquals(MacroValidator.Error.EMPTY_NAME, MacroValidator.validate("   ", ""))
    }

    @Test
    fun nameLongerThanLimit_isRejected() {
        val name = "a".repeat(MacroValidator.MAX_NAME_LENGTH + 1)
        assertEquals(MacroValidator.Error.NAME_TOO_LONG, MacroValidator.validate(name, ""))
    }

    @Test
    fun nameAtLimit_isAccepted() {
        val name = "a".repeat(MacroValidator.MAX_NAME_LENGTH)
        assertNull(MacroValidator.validate(name, ""))
    }

    @Test
    fun nameIsTrimmedBeforeLengthCheck() {
        val name = "  " + "a".repeat(MacroValidator.MAX_NAME_LENGTH) + "  "
        assertNull(MacroValidator.validate(name, ""))
    }

    @Test
    fun descriptionLongerThanLimit_isRejected() {
        val description = "d".repeat(MacroValidator.MAX_DESCRIPTION_LENGTH + 1)
        assertEquals(MacroValidator.Error.DESCRIPTION_TOO_LONG, MacroValidator.validate("ok", description))
    }
}
