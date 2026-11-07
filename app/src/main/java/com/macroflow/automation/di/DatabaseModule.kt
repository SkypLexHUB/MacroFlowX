package com.macroflow.automation.di

import android.content.Context
import androidx.room.Room
import com.macroflow.automation.data.local.MacroFlowDatabase
import com.macroflow.automation.data.local.dao.ExecutionHistoryDao
import com.macroflow.automation.data.local.dao.MacroActionDao
import com.macroflow.automation.data.local.dao.MacroDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MacroFlowDatabase =
        Room.databaseBuilder(context, MacroFlowDatabase::class.java, "macroflow.db").build()

    @Provides
    fun provideMacroDao(db: MacroFlowDatabase): MacroDao = db.macroDao()

    @Provides
    fun provideMacroActionDao(db: MacroFlowDatabase): MacroActionDao = db.macroActionDao()

    @Provides
    fun provideExecutionHistoryDao(db: MacroFlowDatabase): ExecutionHistoryDao = db.executionHistoryDao()
}
