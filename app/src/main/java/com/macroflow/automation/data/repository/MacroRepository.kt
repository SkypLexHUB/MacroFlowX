package com.macroflow.automation.data.repository

import com.macroflow.automation.data.local.dao.MacroActionDao
import com.macroflow.automation.data.local.dao.MacroDao
import com.macroflow.automation.data.local.entity.MacroEntity
import com.macroflow.automation.domain.model.Macro
import com.macroflow.automation.domain.model.MacroAction
import com.macroflow.automation.domain.validation.ActionValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MacroRepository @Inject constructor(
    private val dao: MacroDao,
    private val actionDao: MacroActionDao,
) {
    fun observeMacros(): Flow<List<Macro>> =
        dao.observeAll().map { rows -> rows.map { it.macro.toDomain(it.actionCount) } }

    suspend fun getMacro(id: Long): Macro? = dao.getById(id)?.toDomain()

    suspend fun create(name: String, description: String): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            MacroEntity(
                name = name,
                description = description,
                isFavorite = false,
                createdAt = now,
                updatedAt = now,
                lastRunAt = null,
            ),
        )
    }

    suspend fun updateDetails(id: Long, name: String, description: String) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(name = name, description = description, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) {
        dao.setFavorite(id, favorite, System.currentTimeMillis())
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    suspend fun markRun(id: Long, time: Long) {
        dao.setLastRun(id, time)
    }

    // ---- actions ----

    fun observeActions(macroId: Long): Flow<List<MacroAction>> =
        actionDao.observeForMacro(macroId).map { rows -> rows.map { it.toDomain() } }

    suspend fun getActions(macroId: Long): List<MacroAction> =
        actionDao.getForMacro(macroId).map { it.toDomain() }

    /** Replaces all actions of the macro. Positions are re-numbered from 0. */
    suspend fun replaceActions(macroId: Long, actions: List<MacroAction>) {
        val limited = actions.take(ActionValidator.MAX_ACTIONS_PER_MACRO)
        actionDao.replaceForMacro(macroId, limited.mapIndexed { index, a -> a.toEntity(macroId, index) })
        dao.touch(macroId, System.currentTimeMillis())
    }

    suspend fun appendActions(macroId: Long, newActions: List<MacroAction>) {
        replaceActions(macroId, getActions(macroId) + newActions)
    }
}
