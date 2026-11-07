package com.macroflow.automation.data.repository

import com.macroflow.automation.data.local.dao.ExecutionHistoryDao
import com.macroflow.automation.data.local.entity.ExecutionHistoryEntity
import com.macroflow.automation.domain.model.ExecutionRecord
import com.macroflow.automation.domain.model.ExecutionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val dao: ExecutionHistoryDao,
) {
    fun observeRecent(limit: Int): Flow<List<ExecutionRecord>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    fun observeTotal(): Flow<Int> = dao.observeTotal()

    fun observeCount(status: ExecutionStatus): Flow<Int> = dao.observeCountByStatus(status.name)

    suspend fun record(record: ExecutionRecord) {
        dao.insert(
            ExecutionHistoryEntity(
                macroId = record.macroId,
                macroName = record.macroName,
                startedAt = record.startedAt,
                durationMs = record.durationMs,
                actionCount = record.actionCount,
                status = record.status.name,
                failureReason = record.failureReason,
            ),
        )
    }

    suspend fun clear() = dao.clear()
}

private fun ExecutionHistoryEntity.toDomain() = ExecutionRecord(
    id = id,
    macroId = macroId,
    macroName = macroName,
    startedAt = startedAt,
    durationMs = durationMs,
    actionCount = actionCount,
    status = ExecutionStatus.fromName(status),
    failureReason = failureReason,
)
