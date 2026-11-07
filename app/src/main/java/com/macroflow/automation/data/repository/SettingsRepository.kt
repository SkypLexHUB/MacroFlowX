package com.macroflow.automation.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.macroflow.automation.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val floatingKey = booleanPreferencesKey("floating_enabled")
    private val selectedMacroKey = longPreferencesKey("floating_macro_id")
    private val autoPlayKey = booleanPreferencesKey("auto_play_after_record")
    private val loopKey = booleanPreferencesKey("loop_enabled")

    private val data: Flow<Preferences> = context.settingsDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    val themeMode: Flow<ThemeMode> = data.map { ThemeMode.fromName(it[themeKey]) }

    val floatingEnabled: Flow<Boolean> = data.map { it[floatingKey] ?: false }

    /** The macro that the floating controller runs. Null when none was chosen. */
    val selectedMacroId: Flow<Long?> = data.map { it[selectedMacroKey] }

    /** When on, a macro starts playing right after a recording is saved. Default on. */
    val autoPlayAfterRecord: Flow<Boolean> = data.map { it[autoPlayKey] ?: true }

    /** When on, the floating bar plays macros in a loop until stopped. Default off. */
    val loopEnabled: Flow<Boolean> = data.map { it[loopKey] ?: false }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setFloatingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[floatingKey] = enabled }
    }

    suspend fun setAutoPlayAfterRecord(enabled: Boolean) {
        context.settingsDataStore.edit { it[autoPlayKey] = enabled }
    }

    suspend fun setLoopEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[loopKey] = enabled }
    }

    suspend fun setSelectedMacroId(id: Long) {
        context.settingsDataStore.edit { it[selectedMacroKey] = id }
    }
}
