package com.macroflow.automation.domain.usecase

import com.macroflow.automation.data.repository.MacroRepository
import com.macroflow.automation.domain.validation.MacroValidator
import javax.inject.Inject

/** Validates and then creates (id == null) or updates a macro. Returns a validation error or null on success. */
class SaveMacroUseCase @Inject constructor(
    private val repository: MacroRepository,
) {
    suspend operator fun invoke(id: Long?, name: String, description: String): MacroValidator.Error? {
        val error = MacroValidator.validate(name, description)
        if (error != null) return error
        if (id == null) {
            repository.create(name.trim(), description.trim())
        } else {
            repository.updateDetails(id, name.trim(), description.trim())
        }
        return null
    }
}
