package com.macroflow.automation.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.macroflow.automation.data.repository.HistoryRepository
import com.macroflow.automation.data.repository.MacroRepository
import com.macroflow.automation.data.repository.SettingsRepository
import com.macroflow.automation.domain.model.ExecutionRecord
import com.macroflow.automation.domain.model.ExecutionStatus
import com.macroflow.automation.domain.model.Macro
import com.macroflow.automation.engine.MacroPlayer
import com.macroflow.automation.engine.PlayerState
import com.macroflow.automation.service.MacroAccessibilityService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val totalMacros: Int = 0,
    val totalRuns: Int = 0,
    val successRuns: Int = 0,
    val failedRuns: Int = 0,
    val recent: List<ExecutionRecord> = emptyList(),
    val favorites: List<Macro> = emptyList(),
    val player: PlayerState = PlayerState(),
    val accessibilityEnabled: Boolean = false,
    val floatingEnabled: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    macroRepository: MacroRepository,
    historyRepository: HistoryRepository,
    player: MacroPlayer,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val stats = combine(
        macroRepository.observeMacros(),
        historyRepository.observeTotal(),
        historyRepository.observeCount(ExecutionStatus.SUCCESS),
        historyRepository.observeCount(ExecutionStatus.FAILED),
        historyRepository.observeRecent(5),
    ) { macros, total, success, failed, recent ->
        HomeUiState(
            isLoading = false,
            totalMacros = macros.size,
            totalRuns = total,
            successRuns = success,
            failedRuns = failed,
            recent = recent,
            favorites = macros.filter { it.isFavorite },
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        stats,
        player.state,
        MacroAccessibilityService.connected,
        settings.floatingEnabled,
    ) { base, playerState, accessibility, floating ->
        base.copy(player = playerState, accessibilityEnabled = accessibility, floatingEnabled = floating)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setFloatingEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setFloatingEnabled(enabled) }
    }
}
