package com.macroflow.automation.ui.macros

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macroflow.automation.data.repository.MacroRepository
import com.macroflow.automation.data.repository.SettingsRepository
import com.macroflow.automation.domain.model.Macro
import com.macroflow.automation.domain.usecase.SaveMacroUseCase
import com.macroflow.automation.domain.validation.MacroValidator
import com.macroflow.automation.engine.MacroPlayer
import com.macroflow.automation.service.MacroAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditDialogState(
    val id: Long?,
    val name: String,
    val description: String,
    val error: MacroValidator.Error? = null,
)

data class RunDialogState(
    val macro: Macro,
    val repeat: String = "1",
    val delaySec: String = "3",
    val loop: Boolean = false,
    val hasError: Boolean = false,
)

data class MacrosUiState(
    val isLoading: Boolean = true,
    val macros: List<Macro> = emptyList(),
    val totalCount: Int = 0,
    val query: String = "",
    val dialog: EditDialogState? = null,
    val runDialog: RunDialogState? = null,
    val pendingDelete: Macro? = null,
    val selectedMacroId: Long? = null,
    val hasError: Boolean = false,
)

private data class LocalState(
    val query: String = "",
    val dialog: EditDialogState? = null,
    val runDialog: RunDialogState? = null,
    val pendingDelete: Macro? = null,
    val hasError: Boolean = false,
)

@HiltViewModel
class MacrosViewModel @Inject constructor(
    private val repository: MacroRepository,
    private val saveMacro: SaveMacroUseCase,
    private val player: MacroPlayer,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    private val _moveToBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emitted when a run with a start delay begins, so the screen can send the app to the background. */
    val moveToBack: SharedFlow<Unit> = _moveToBack

    val uiState: StateFlow<MacrosUiState> = combine(
        repository.observeMacros(),
        local,
        settings.selectedMacroId,
    ) { macros, l, selectedId ->
        val q = l.query.trim()
        val visible = if (q.isEmpty()) {
            macros
        } else {
            macros.filter { it.name.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true) }
        }
        MacrosUiState(
            isLoading = false,
            macros = visible,
            totalCount = macros.size,
            query = l.query,
            dialog = l.dialog,
            runDialog = l.runDialog,
            pendingDelete = l.pendingDelete,
            selectedMacroId = selectedId,
            hasError = l.hasError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MacrosUiState())

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    // ---- create / rename ----

    fun openCreate() = local.update { it.copy(dialog = EditDialogState(id = null, name = "", description = "")) }

    fun openEdit(macro: Macro) = local.update {
        it.copy(dialog = EditDialogState(id = macro.id, name = macro.name, description = macro.description))
    }

    fun onDialogChange(name: String, description: String) = local.update {
        it.copy(dialog = it.dialog?.copy(name = name, description = description, error = null))
    }

    fun dismissDialog() = local.update { it.copy(dialog = null) }

    fun save() {
        val dialog = local.value.dialog ?: return
        viewModelScope.launch {
            try {
                val error = saveMacro(dialog.id, dialog.name, dialog.description)
                if (error != null) {
                    local.update { it.copy(dialog = it.dialog?.copy(error = error)) }
                } else {
                    local.update { it.copy(dialog = null) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(hasError = true) }
            }
        }
    }

    // ---- favorite / floating macro ----

    fun toggleFavorite(macro: Macro) = launchSafely { repository.setFavorite(macro.id, !macro.isFavorite) }

    fun useInFloating(macro: Macro) = launchSafely { settings.setSelectedMacroId(macro.id) }

    // ---- run ----

    fun openRun(macro: Macro) = local.update { it.copy(runDialog = RunDialogState(macro)) }

    fun onRunChange(repeat: String, delaySec: String, loop: Boolean) = local.update {
        it.copy(runDialog = it.runDialog?.copy(repeat = repeat, delaySec = delaySec, loop = loop, hasError = false))
    }

    fun dismissRun() = local.update { it.copy(runDialog = null) }

    fun confirmRun() {
        val dialog = local.value.runDialog ?: return
        val repeat = if (dialog.loop) 1 else dialog.repeat.trim().toIntOrNull()
        val delaySec = dialog.delaySec.trim().toIntOrNull()
        if (repeat == null || delaySec == null ||
            repeat !in 1..MacroPlayer.MAX_REPEAT || delaySec !in 0..MacroPlayer.MAX_START_DELAY_SEC
        ) {
            local.update { it.copy(runDialog = it.runDialog?.copy(hasError = true)) }
            return
        }
        local.update { it.copy(runDialog = null) }
        player.start(dialog.macro.id, repeat, delaySec, loopForever = dialog.loop)
        val accepted = player.state.value.error == null
        if (accepted && delaySec > 0 && MacroAccessibilityService.connected.value) {
            _moveToBack.tryEmit(Unit)
        }
    }

    // ---- delete ----

    fun requestDelete(macro: Macro) = local.update { it.copy(pendingDelete = macro) }

    fun cancelDelete() = local.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val macro = local.value.pendingDelete ?: return
        local.update { it.copy(pendingDelete = null) }
        launchSafely { repository.delete(macro.id) }
    }

    fun clearError() = local.update { it.copy(hasError = false) }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(hasError = true) }
            }
        }
    }
}
