package com.macroflow.automation.ui.macros

import android.app.Activity
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.macroflow.automation.domain.model.Macro
import com.macroflow.automation.domain.validation.MacroValidator
import com.macroflow.automation.ui.common.AppCard
import com.macroflow.automation.ui.common.EmptyState
import com.macroflow.automation.ui.theme.WarningAmber

@Composable
fun MacrosScreen(
    onOpenEditor: (Long) -> Unit,
    viewModel: MacrosViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.error_generic)
    val context = LocalContext.current

    LaunchedEffect(state.hasError) {
        if (state.hasError) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.clearError()
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
                onClick = viewModel::openCreate,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.macro_new)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.macros_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.macros_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.totalCount == 0 -> EmptyState(
                        icon = Icons.Filled.Add,
                        title = stringResource(R.string.macros_empty_title),
                        body = stringResource(R.string.macros_empty_body),
                        modifier = Modifier.align(Alignment.Center),
                    )
                    state.macros.isEmpty() -> EmptyState(
                        icon = Icons.Filled.Search,
                        title = stringResource(R.string.macros_no_results),
                        body = "",
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.macros, key = { it.id }) { macro ->
                            MacroCard(
                                macro = macro,
                                isFloatingMacro = macro.id == state.selectedMacroId,
                                onOpen = { onOpenEditor(macro.id) },
                                onFavorite = { viewModel.toggleFavorite(macro) },
                                onRun = { viewModel.openRun(macro) },
                                onRename = { viewModel.openEdit(macro) },
                                onUseInFloating = { viewModel.useInFloating(macro) },
                                onDelete = { viewModel.requestDelete(macro) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.dialog?.let { dialog ->
        EditMacroDialog(
            dialog = dialog,
            onChange = viewModel::onDialogChange,
            onSave = viewModel::save,
            onDismiss = viewModel::dismissDialog,
        )
    }

    state.runDialog?.let { run ->
        RunDialog(
            state = run,
            onChange = viewModel::onRunChange,
            onStart = viewModel::confirmRun,
            onDismiss = viewModel::dismissRun,
        )
    }

    state.pendingDelete?.let { macro ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.delete_macro_title)) },
            text = { Text(stringResource(R.string.delete_macro_message, macro.name)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun MacroCard(
    macro: Macro,
    isFloatingMacro: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onRun: () -> Unit,
    onRename: () -> Unit,
    onUseInFloating: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    AppCard(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = macro.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (macro.description.isNotBlank()) {
                    Text(
                        text = macro.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(R.string.macro_actions_count, macro.actionCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    imageVector = if (macro.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = stringResource(R.string.cd_favorite),
                    tint = if (macro.isFavorite) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRun) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.cd_run),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_edit_actions)) },
                        onClick = {
                            menuOpen = false
                            onOpen()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.macro_edit)) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (isFloatingMacro) R.string.menu_floating_selected else R.string.menu_use_floating,
                                ),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onUseInFloating()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EditMacroDialog(
    dialog: EditDialogState,
    onChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val nameError = when (dialog.error) {
        MacroValidator.Error.EMPTY_NAME -> stringResource(R.string.error_name_empty)
        MacroValidator.Error.NAME_TOO_LONG -> stringResource(R.string.error_name_long, MacroValidator.MAX_NAME_LENGTH)
        else -> null
    }
    val descriptionError = when (dialog.error) {
        MacroValidator.Error.DESCRIPTION_TOO_LONG ->
            stringResource(R.string.error_desc_long, MacroValidator.MAX_DESCRIPTION_LENGTH)
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (dialog.id == null) R.string.macro_new else R.string.macro_edit))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = { onChange(it, dialog.description) },
                    label = { Text(stringResource(R.string.macro_name)) },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dialog.description,
                    onValueChange = { onChange(dialog.name, it) },
                    label = { Text(stringResource(R.string.macro_description)) },
                    minLines = 2,
                    maxLines = 4,
                    isError = descriptionError != null,
                    supportingText = descriptionError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
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
private fun RunDialog(
    state: RunDialogState,
    onChange: (String, String) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.run_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = state.macro.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                OutlinedTextField(
                    value = state.repeat,
                    onValueChange = { onChange(it, state.delaySec) },
                    label = { Text(stringResource(R.string.run_repeat)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.hasError,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.delaySec,
                    onValueChange = { onChange(state.repeat, it) },
                    label = { Text(stringResource(R.string.run_delay)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.hasError,
                    supportingText = if (state.hasError) {
                        { Text(stringResource(R.string.error_run_invalid)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.run_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onStart) { Text(stringResource(R.string.run_start)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
