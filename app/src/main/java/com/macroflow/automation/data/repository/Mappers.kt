package com.macroflow.automation.data.repository

import com.macroflow.automation.data.local.entity.MacroActionEntity
import com.macroflow.automation.data.local.entity.MacroEntity
import com.macroflow.automation.domain.model.ActionType
import com.macroflow.automation.domain.model.Macro
import com.macroflow.automation.domain.model.MacroAction

internal fun MacroEntity.toDomain(actionCount: Int = 0) = Macro(
    id = id,
    name = name,
    description = description,
    isFavorite = isFavorite,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastRunAt = lastRunAt,
    actionCount = actionCount,
)

internal fun MacroActionEntity.toDomain() = MacroAction(
    id = id,
    position = position,
    type = ActionType.fromName(type),
    x = x,
    y = y,
    endX = endX,
    endY = endY,
    durationMs = durationMs,
    delayMs = delayMs,
    repeatCount = repeatCount,
    enabled = enabled,
)

internal fun MacroAction.toEntity(macroId: Long, position: Int) = MacroActionEntity(
    macroId = macroId,
    position = position,
    type = type.name,
    x = x,
    y = y,
    endX = endX,
    endY = endY,
    durationMs = durationMs,
    delayMs = delayMs,
    repeatCount = repeatCount,
    enabled = enabled,
)
