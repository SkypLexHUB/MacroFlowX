package com.macroflow.automation.ui.player

import androidx.lifecycle.ViewModel
import com.macroflow.automation.engine.MacroPlayer
import com.macroflow.automation.engine.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val player: MacroPlayer,
) : ViewModel() {
    val state: StateFlow<PlayerState> = player.state

    fun pause() = player.pause()
    fun resume() = player.resume()
    fun stop() = player.stop()
    fun clearError() = player.clearError()
}
