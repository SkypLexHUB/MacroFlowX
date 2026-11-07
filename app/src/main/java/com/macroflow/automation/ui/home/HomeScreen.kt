package com.macroflow.automation.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macroflow.automation.R
import com.macroflow.automation.domain.model.ExecutionRecord
import com.macroflow.automation.domain.model.ExecutionStatus
import com.macroflow.automation.engine.PlayerStatus
import com.macroflow.automation.ui.common.AccessibilityCard
import com.macroflow.automation.ui.common.AppCard
import com.macroflow.automation.ui.common.SectionTitle
import com.macroflow.automation.ui.common.formatDateTime
import com.macroflow.automation.ui.common.formatDuration
import com.macroflow.automation.ui.theme.AccentPurple
import com.macroflow.automation.ui.theme.ErrorRed
import com.macroflow.automation.ui.theme.PrimaryBlue
import com.macroflow.automation.ui.theme.SuccessGreen
import com.macroflow.automation.ui.theme.WarningAmber

@Composable
fun HomeScreen(
    onManageMacros: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { StatusCard(state.player.status) }
        item { AccessibilityCard(enabled = state.accessibilityEnabled) }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.floating_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.floatingEnabled && state.accessibilityEnabled,
                        onCheckedChange = viewModel::setFloatingEnabled,
                        enabled = state.accessibilityEnabled,
                    )
                }
                Text(
                    text = stringResource(
                        if (state.accessibilityEnabled) R.string.floating_body else R.string.floating_need_acc,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), R.string.stat_macros, state.totalMacros, PrimaryBlue)
                StatCard(Modifier.weight(1f), R.string.stat_runs, state.totalRuns, AccentPurple)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), R.string.stat_success, state.successRuns, SuccessGreen)
                StatCard(Modifier.weight(1f), R.string.stat_failed, state.failedRuns, ErrorRed)
            }
        }
        item { SectionTitle(stringResource(R.string.quick_actions)) }
        item {
            Button(onClick = onManageMacros, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_manage_macros))
            }
        }
        item { SectionTitle(stringResource(R.string.recent_activity)) }
        if (state.recent.isEmpty()) {
            item { EmptyHint(R.string.recent_empty) }
        } else {
            items(state.recent, key = { "recent-${it.id}" }) { record -> RecentRow(record) }
        }
        item { SectionTitle(stringResource(R.string.favorites)) }
        if (state.favorites.isEmpty()) {
            item { EmptyHint(R.string.favorites_empty) }
        } else {
            items(state.favorites, key = { "fav-${it.id}" }) { macro ->
                AppCard(Modifier.fillMaxWidth()) {
                    Text(
                        text = macro.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.macro_actions_count, macro.actionCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(@StringRes text: Int) {
    AppCard(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusCard(status: PlayerStatus) {
    val label = when (status) {
        PlayerStatus.IDLE -> R.string.status_idle
        PlayerStatus.STARTING -> R.string.status_starting
        PlayerStatus.PLAYING -> R.string.status_playing
        PlayerStatus.PAUSED -> R.string.status_paused
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.horizontalGradient(listOf(PrimaryBlue, AccentPurple)))
            .padding(20.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.home_status_label),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, @StringRes label: Int, value: Int, accent: Color) {
    AppCard(modifier) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentRow(record: ExecutionRecord) {
    val (labelRes, color) = when (record.status) {
        ExecutionStatus.SUCCESS -> R.string.exec_status_success to SuccessGreen
        ExecutionStatus.FAILED -> R.string.exec_status_failed to ErrorRed
        ExecutionStatus.STOPPED -> R.string.exec_status_stopped to WarningAmber
    }
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = record.macroName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.exec_row_detail,
                        formatDateTime(record.startedAt),
                        formatDuration(record.durationMs),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
        }
    }
}
