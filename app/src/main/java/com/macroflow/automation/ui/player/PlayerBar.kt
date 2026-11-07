package com.macroflow.automation.ui.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.macroflow.automation.R
import com.macroflow.automation.engine.PlayerState
import com.macroflow.automation.engine.PlayerStatus
import com.macroflow.automation.ui.theme.ErrorRed

/** Shown above the navigation bar while a macro is running. Has the in-app Stop button. */
@Composable
fun PlayerBar(
    state: PlayerState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    if (state.status == PlayerStatus.IDLE) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (state.status == PlayerStatus.STARTING) {
                    stringResource(R.string.player_bar_starting, state.macroName)
                } else if (state.loopForever) {
                    stringResource(
                        R.string.player_bar_looping,
                        state.macroName,
                        state.currentStep,
                        state.totalSteps,
                        state.loop,
                    )
                } else {
                    stringResource(
                        R.string.player_bar_running,
                        state.macroName,
                        state.currentStep,
                        state.totalSteps,
                        state.loop,
                        state.loops,
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            when (state.status) {
                PlayerStatus.PLAYING -> IconButton(onClick = onPause) {
                    Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.cd_pause))
                }
                PlayerStatus.PAUSED -> IconButton(onClick = onResume) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.cd_resume))
                }
                else -> Unit
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.cd_stop), tint = ErrorRed)
            }
        }
    }
}
