package com.macroflow.automation.engine

import com.macroflow.automation.data.repository.HistoryRepository
import com.macroflow.automation.data.repository.MacroRepository
import com.macroflow.automation.domain.model.ActionType
import com.macroflow.automation.domain.model.ExecutionRecord
import com.macroflow.automation.domain.model.ExecutionStatus
import com.macroflow.automation.domain.model.MacroAction
import com.macroflow.automation.domain.validation.ActionValidator
import com.macroflow.automation.service.MacroAccessibilityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays a macro through the accessibility service. One macro at a time.
 * Every run (success, failure or stopped by the user) is written to the execution history.
 */
@Singleton
class MacroPlayer @Inject constructor(
    private val macros: MacroRepository,
    private val history: HistoryRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private val paused = MutableStateFlow(false)
    private var job: Job? = null

    /** Starts a macro. [repeat] is capped to [MAX_REPEAT] and [startDelaySec] to [MAX_START_DELAY_SEC]. */
    fun start(macroId: Long, repeat: Int = 1, startDelaySec: Int = 0) {
        synchronized(this) {
            if (_state.value.status != PlayerStatus.IDLE) {
                _state.update { it.copy(error = PlayerError.ALREADY_RUNNING) }
                return
            }
            if (MacroAccessibilityService.instance == null) {
                _state.value = PlayerState(error = PlayerError.NO_ACCESSIBILITY)
                return
            }
            paused.value = false
            _state.value = PlayerState(status = PlayerStatus.STARTING, macroId = macroId)
            job = scope.launch {
                execute(
                    macroId = macroId,
                    repeat = repeat.coerceIn(1, MAX_REPEAT),
                    startDelaySec = startDelaySec.coerceIn(0, MAX_START_DELAY_SEC),
                )
            }
        }
    }

    fun pause() {
        if (_state.value.status == PlayerStatus.PLAYING) {
            paused.value = true
            _state.update { it.copy(status = PlayerStatus.PAUSED) }
        }
    }

    fun resume() {
        if (_state.value.status == PlayerStatus.PAUSED) {
            paused.value = false
            _state.update { it.copy(status = PlayerStatus.PLAYING) }
        }
    }

    /** Emergency stop: cancels the run immediately, also while paused or counting down. */
    fun stop() {
        job?.cancel()
    }

    fun clearError() {
        _state.update { if (it.error != null) it.copy(error = null) else it }
    }

    private suspend fun execute(macroId: Long, repeat: Int, startDelaySec: Int) {
        val macro = macros.getMacro(macroId)
        if (macro == null) {
            _state.value = PlayerState(error = PlayerError.MACRO_NOT_FOUND)
            return
        }
        val actions = macros.getActions(macroId).filter { it.enabled }
        if (actions.isEmpty()) {
            _state.value = PlayerState(error = PlayerError.EMPTY_MACRO)
            return
        }
        if (actions.any { ActionValidator.validate(it) != null }) {
            _state.value = PlayerState(error = PlayerError.INVALID_ACTION)
            return
        }

        _state.value = PlayerState(
            status = PlayerStatus.STARTING,
            macroId = macroId,
            macroName = macro.name,
            totalSteps = actions.size,
            loops = repeat,
        )
        val startedAt = System.currentTimeMillis()
        var playerError: PlayerError? = null
        var failure: String? = null
        var executed = 0
        try {
            if (startDelaySec > 0) delay(startDelaySec * 1000L)
            _state.update { it.copy(status = PlayerStatus.PLAYING) }
            val runStart = System.currentTimeMillis()
            loops@ for (round in 1..repeat) {
                _state.update { it.copy(loop = round) }
                for ((index, action) in actions.withIndex()) {
                    awaitResumed()
                    if (System.currentTimeMillis() - runStart > MAX_RUN_MS) {
                        playerError = PlayerError.TIME_LIMIT
                        break@loops
                    }
                    _state.update { it.copy(currentStep = index + 1) }
                    if (MacroAccessibilityService.instance == null) {
                        playerError = PlayerError.NO_ACCESSIBILITY
                        break@loops
                    }
                    if (!perform(action)) {
                        playerError = PlayerError.GESTURE_FAILED
                        break@loops
                    }
                    executed++
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            playerError = PlayerError.GESTURE_FAILED
            failure = e.javaClass.simpleName
        } finally {
            val cancelled = !currentCoroutineContext().isActive
            withContext(NonCancellable) {
                val status = when {
                    cancelled -> ExecutionStatus.STOPPED
                    playerError != null -> ExecutionStatus.FAILED
                    else -> ExecutionStatus.SUCCESS
                }
                try {
                    history.record(
                        ExecutionRecord(
                            id = 0,
                            macroId = macroId,
                            macroName = macro.name,
                            startedAt = startedAt,
                            durationMs = System.currentTimeMillis() - startedAt,
                            actionCount = executed,
                            status = status,
                            failureReason = playerError?.name ?: failure,
                        ),
                    )
                    macros.markRun(macroId, startedAt)
                } catch (e: Exception) {
                    // History is best effort; never let it break the player state.
                }
                paused.value = false
                _state.value = PlayerState(error = if (cancelled) null else playerError)
            }
        }
    }

    private suspend fun perform(action: MacroAction): Boolean {
        if (action.delayMs > 0) {
            delay(action.delayMs)
            awaitResumed()
        }
        val service = MacroAccessibilityService.instance ?: return false
        if (action.type == ActionType.DELAY) {
            delay(action.durationMs)
            return true
        }
        for (i in 1..action.repeatCount) {
            awaitResumed()
            if (!service.perform(action)) return false
            if (i < action.repeatCount) delay(GAP_BETWEEN_REPEATS_MS)
        }
        return true
    }

    private suspend fun awaitResumed() {
        if (paused.value) paused.first { !it }
    }

    companion object {
        const val MAX_REPEAT = 999
        const val MAX_START_DELAY_SEC = 30
        /** Safety limit for one run, regardless of repeat count. */
        const val MAX_RUN_MS = 30 * 60 * 1000L
        private const val GAP_BETWEEN_REPEATS_MS = 100L
    }
}
