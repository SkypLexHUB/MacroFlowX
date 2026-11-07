package com.macroflow.automation.ui.editor

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.macroflow.automation.R
import com.macroflow.automation.domain.model.ActionType
import com.macroflow.automation.domain.model.MacroAction
import com.macroflow.automation.ui.common.AppCard
import com.macroflow.automation.ui.common.EmptyState
import com.macroflow.automation.ui.theme.ErrorRed

@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val messages = mapOf(
        EditorMessage.NO_ACCESSIBILITY to stringResource(R.string.err_no_acc),
        EditorMessage.RECORD_FAILED to stringResource(R.string.error_generic),
        EditorMessage.ERROR to stringResource(R.string.error_generic),
    )

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(messages.getValue(message))
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.moveToBack.collect { (context as? Activity)?.moveTaskToBack(true) }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::openAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_add)) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
                Text(
                    text = state.macroName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::startRecording) {
                    Icon(
                        Icons.Filled.FiberManualRecord,
                        contentDescription = stringResource(R.string.cd_record),
                        tint = ErrorRed,
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                if (!state.isLoading && state.actions.isEmpty()) {
                    EmptyState(
                        icon = Icons.Filled.Edit,
                        title = stringResource(R.string.editor_empty_title),
                        body = stringResource(R.string.editor_empty_body),
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(state.actions, key = { index, a -> "${a.id}-$index" }) { index, action ->
                            ActionRow(
                                index = index,
                                action = action,
                                isFirst = index == 0,
                                isLast = index == state.actions.lastIndex,
                                onToggle = { viewModel.toggleEnabled(index) },
                                onUp = { viewModel.moveUp(index) },
                                onDown = { viewModel.moveDown(index) },
                                onEdit = { viewModel.openEdit(index) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.dialog?.let { dialog ->
        ActionDialog(
            dialog = dialog,
            onChange = viewModel::onDialogChange,
            onSelectType = viewModel::selectType,
            onSave = viewModel::saveDialog,
            onDelete = viewModel::deleteFromDialog,
            onDuplicate = viewModel::duplicateFromDialog,
            onDismiss = viewModel::dismissDialog,
        )
    }
}

@Composable
private fun ActionRow(
    index: Int,
    action: MacroAction,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit,
) {
    val summary = when (action.type) {
        ActionType.TAP -> stringResource(R.string.summary_tap, action.x.toPercentInt(), action.y.toPercentInt())
        ActionType.LONG_PRESS -> stringResource(
            R.string.summary_long, action.x.toPercentInt(), action.y.toPercentInt(), action.durationMs,
        )
        ActionType.SWIPE -> stringResource(
            R.string.summary_swipe,
            action.x.toPercentInt(), action.y.toPercentInt(),
            action.endX.toPercentInt(), action.endY.toPercentInt(),
            action.durationMs,
        )
        ActionType.DELAY -> stringResource(R.string.summary_delay, action.durationMs)
    }
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(Modifier.weight(1f)) {
                val dim = if (action.enabled) 1f else 0.45f
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                )
                if (action.type != ActionType.DELAY) {
                    Text(
                        text = stringResource(R.string.summary_meta, action.delayMs, action.repeatCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim),
                    )
                }
            }
            Switch(checked = action.enabled, onCheckedChange = { onToggle() })
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onUp, enabled = !isFirst) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.cd_move_up))
            }
            IconButton(onClick = onDown, enabled = !isLast) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_move_down))
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cd_edit_action))
            }
        }
    }
}

@Composable
private fun ActionDialog(
    dialog: ActionDialogState,
    onChange: (ActionDialogState) -> Unit,
    onSelectType: (ActionType) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val type = dialog.type
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (dialog.index == null) R.string.action_add else R.string.action_edit))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeButton(R.string.type_tap, type == ActionType.TAP, Modifier.weight(1f)) {
                        onSelectType(ActionType.TAP)
                    }
                    TypeButton(R.string.type_long, type == ActionType.LONG_PRESS, Modifier.weight(1f)) {
                        onSelectType(ActionType.LONG_PRESS)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeButton(R.string.type_swipe, type == ActionType.SWIPE, Modifier.weight(1f)) {
                        onSelectType(ActionType.SWIPE)
                    }
                    TypeButton(R.string.type_delay, type == ActionType.DELAY, Modifier.weight(1f)) {
                        onSelectType(ActionType.DELAY)
                    }
                }
                if (type != ActionType.DELAY) {
                    NumberField(R.string.field_x, dialog.x, decimal = true) { onChange(dialog.copy(x = it)) }
                    NumberField(R.string.field_y, dialog.y, decimal = true) { onChange(dialog.copy(y = it)) }
                }
                if (type == ActionType.SWIPE) {
                    NumberField(R.string.field_end_x, dialog.endX, decimal = true) { onChange(dialog.copy(endX = it)) }
                    NumberField(R.string.field_end_y, dialog.endY, decimal = true) { onChange(dialog.copy(endY = it)) }
                }
                if (type != ActionType.TAP) {
                    NumberField(R.string.field_duration, dialog.duration) { onChange(dialog.copy(duration = it)) }
                }
                NumberField(R.string.field_wait, dialog.wait) { onChange(dialog.copy(wait = it)) }
                if (type != ActionType.DELAY) {
                    NumberField(R.string.field_repeat, dialog.repeat) { onChange(dialog.copy(repeat = it)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.field_enabled),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = dialog.enabled, onCheckedChange = { onChange(dialog.copy(enabled = it)) })
                }
                if (dialog.hasError) {
                    Text(
                        text = stringResource(R.string.error_action_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (dialog.index != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDuplicate, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.action_duplicate))
                        }
                        OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun TypeButton(labelRes: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(stringResource(labelRes), maxLines = 1) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(stringResource(labelRes), maxLines = 1) }
    }
}

@Composable
private fun NumberField(labelRes: Int, value: String, decimal: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelRes)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
