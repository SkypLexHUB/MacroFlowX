package com.macroflow.automation.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macroflow.automation.data.repository.MacroRepository
import com.macroflow.automation.domain.model.ActionType
import com.macroflow.automation.domain.model.MacroAction
import com.macroflow.automation.domain.validation.ActionValidator
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
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

enum class EditorMessage { NO_ACCESSIBILITY, RECORD_FAILED, ERROR }

/** Text fields of the add/edit dialog. Positions are shown as percent (0-100). */
data class ActionDialogState(
    val index: Int?,
    val type: ActionType,
    val x: String,
    val y: String,
    val endX: String,
    val endY: String,
    val duration: String,
    val wait: String,
    val repeat: String,
    val enabled: Boolean,
    val hasError: Boolean = false,
)

data class EditorUiState(
    val isLoading: Boolean = true,
    val macroName: String = "",
    val actions: List<MacroAction> = emptyList(),
    val dialog: ActionDialogState? = null,
    val message: EditorMessage? = null,
)

private data class LocalState(
    val name: String = "",
    val dialog: ActionDialogState? = null,
    val message: EditorMessage? = null,
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MacroRepository,
) : ViewModel() {

    private val macroId: Long = checkNotNull(savedStateHandle.get<Long>("macroId"))
    private val local = MutableStateFlow(LocalState())
    private val _moveToBack = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emitted after recording starts so the screen can leave the app. */
    val moveToBack: SharedFlow<Unit> = _moveToBack

    val uiState: StateFlow<EditorUiState> = combine(repository.observeActions(macroId), local) { actions, l ->
        EditorUiState(isLoading = false, macroName = l.name, actions = actions, dialog = l.dialog, message = l.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUiState())

    init {
        viewModelScope.launch {
            val macro = repository.getMacro(macroId)
            local.update { it.copy(name = macro?.name ?: "") }
        }
    }

    fun clearMessage() = local.update { it.copy(message = null) }

    // ---- list operations (each one is saved immediately) ----

    fun moveUp(index: Int) = mutate { list ->
        if (index <= 0 || index >= list.size) list else list.toMutableList().also { it.add(index - 1, it.removeAt(index)) }
    }

    fun moveDown(index: Int) = mutate { list ->
        if (index < 0 || index >= list.size - 1) list else list.toMutableList().also { it.add(index + 1, it.removeAt(index)) }
    }

    fun toggleEnabled(index: Int) = mutate { list ->
        list.mapIndexed { i, a -> if (i == index) a.copy(enabled = !a.enabled) else a }
    }

    private fun mutate(transform: (List<MacroAction>) -> List<MacroAction>) {
        viewModelScope.launch {
            try {
                repository.replaceActions(macroId, transform(uiState.value.actions))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(message = EditorMessage.ERROR) }
            }
        }
    }

    // ---- dialog ----

    fun openAdd() = local.update { it.copy(dialog = defaultsFor(ActionType.TAP, index = null)) }

    fun openEdit(index: Int) {
        val action = uiState.value.actions.getOrNull(index) ?: return
        local.update { it.copy(dialog = action.toDialog(index)) }
    }

    fun selectType(type: ActionType) = local.update {
        val current = it.dialog ?: return@update it
        it.copy(dialog = defaultsFor(type, current.index).copy(wait = current.wait, repeat = current.repeat, enabled = current.enabled))
    }

    fun onDialogChange(dialog: ActionDialogState) = local.update { it.copy(dialog = dialog.copy(hasError = false)) }

    fun dismissDialog() = local.update { it.copy(dialog = null) }

    fun saveDialog() {
        val dialog = local.value.dialog ?: return
        val action = dialog.toAction()
        if (action == null || ActionValidator.validate(action) != null) {
            local.update { it.copy(dialog = it.dialog?.copy(hasError = true)) }
            return
        }
        local.update { it.copy(dialog = null) }
        mutate { list ->
            val index = dialog.index
            if (index == null) {
                list + action
            } else {
                list.mapIndexed { i, old -> if (i == index) action.copy(id = old.id) else old }
            }
        }
    }

    fun deleteFromDialog() {
        val index = local.value.dialog?.index ?: return
        local.update { it.copy(dialog = null) }
        mutate { list -> list.filterIndexed { i, _ -> i != index } }
    }

    fun duplicateFromDialog() {
        val index = local.value.dialog?.index ?: return
        local.update { it.copy(dialog = null) }
        mutate { list ->
            val original = list.getOrNull(index) ?: return@mutate list
            list.toMutableList().also { it.add(index + 1, original.copy(id = 0)) }
        }
    }

    // ---- recording ----

    fun startRecording() {
        val service = MacroAccessibilityService.instance
        if (service == null) {
            local.update { it.copy(message = EditorMessage.NO_ACCESSIBILITY) }
            return
        }
        if (service.startRecording(macroId)) {
            _moveToBack.tryEmit(Unit)
        } else {
            local.update { it.copy(message = EditorMessage.RECORD_FAILED) }
        }
    }
}

private fun pct(v: Float): String {
    val p = v * 100f
    return if (p % 1f == 0f) p.toInt().toString() else String.format(Locale.US, "%.1f", p)
}

private fun defaultsFor(type: ActionType, index: Int?) = ActionDialogState(
    index = index,
    type = type,
    x = "50",
    y = if (type == ActionType.SWIPE) "70" else "50",
    endX = "50",
    endY = "30",
    duration = when (type) {
        ActionType.TAP -> ""
        ActionType.LONG_PRESS -> "800"
        ActionType.SWIPE -> "400"
        ActionType.DELAY -> "1000"
    },
    wait = "500",
    repeat = "1",
    enabled = true,
)

private fun MacroAction.toDialog(index: Int) = ActionDialogState(
    index = index,
    type = type,
    x = pct(x),
    y = pct(y),
    endX = pct(endX),
    endY = pct(endY),
    duration = if (type == ActionType.TAP) "" else durationMs.toString(),
    wait = delayMs.toString(),
    repeat = repeatCount.toString(),
    enabled = enabled,
)

/** Returns null when any field cannot be parsed. Range checks are done by [ActionValidator]. */
private fun ActionDialogState.toAction(): MacroAction? {
    fun percent(s: String): Float? = s.trim().replace(',', '.').toFloatOrNull()?.div(100f)
    val usesPosition = type != ActionType.DELAY
    val px = if (usesPosition) percent(x) else 0.5f
    val py = if (usesPosition) percent(y) else 0.5f
    val ex = if (type == ActionType.SWIPE) percent(endX) else px
    val ey = if (type == ActionType.SWIPE) percent(endY) else py
    val durationMs = if (type == ActionType.TAP) 0L else duration.trim().toLongOrNull()
    val waitMs = wait.trim().toLongOrNull()
    val repeatCount = if (type == ActionType.DELAY) 1 else repeat.trim().toIntOrNull()
    if (px == null || py == null || ex == null || ey == null ||
        durationMs == null || waitMs == null || repeatCount == null
    ) {
        return null
    }
    return MacroAction(
        type = type,
        x = px,
        y = py,
        endX = ex,
        endY = ey,
        durationMs = durationMs,
        delayMs = waitMs,
        repeatCount = repeatCount,
        enabled = enabled,
    )
}

/** Rounded percent values used in the list summaries. */
internal fun Float.toPercentInt(): Int = (this * 100f).roundToInt()
