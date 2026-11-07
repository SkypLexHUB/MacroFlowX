package com.macroflow.automation.service

import com.macroflow.automation.data.repository.MacroRepository
import com.macroflow.automation.data.repository.SettingsRepository
import com.macroflow.automation.engine.MacroPlayer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Lets the accessibility service reach the Hilt graph without being a Hilt-injected class. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServiceEntryPoint {
    fun player(): MacroPlayer
    fun macros(): MacroRepository
    fun settings(): SettingsRepository
}
