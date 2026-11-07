package com.macroflow.automation.domain.validation

object MacroValidator {
    const val MAX_NAME_LENGTH = 60
    const val MAX_DESCRIPTION_LENGTH = 300

    enum class Error { EMPTY_NAME, NAME_TOO_LONG, DESCRIPTION_TOO_LONG }

    /** Returns null when the input is valid. Name and description are validated after trimming. */
    fun validate(name: String, description: String): Error? {
        val trimmedName = name.trim()
        return when {
            trimmedName.isEmpty() -> Error.EMPTY_NAME
            trimmedName.length > MAX_NAME_LENGTH -> Error.NAME_TOO_LONG
            description.trim().length > MAX_DESCRIPTION_LENGTH -> Error.DESCRIPTION_TOO_LONG
            else -> null
        }
    }
}
