package com.example.moneytap.data.repository

import com.example.moneytap.data.database.MoneyTapDatabase
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.RuleCondition
import com.example.moneytap.domain.model.UserCategorizationRule
import com.example.moneytap.domain.repository.UserRuleRepository
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

class UserRuleRepositoryImpl(
    private val database: MoneyTapDatabase,
    private val ioDispatcher: CoroutineContext,
) : UserRuleRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun saveRule(rule: UserCategorizationRule) = withContext(ioDispatcher) {
        database.userCategorizationRuleQueries.insertRule(
            id = rule.id,
            name = rule.name,
            conditions = json.encodeToString(rule.conditions),
            category = rule.category.name,
            priority = rule.priority.toLong(),
            learnedFromExamples = rule.learnedFromExamples?.let { json.encodeToString(it) },
            enabled = if (rule.enabled) 1L else 0L,
            createdAt = rule.createdAt.toEpochMilliseconds(),
        )
    }

    override suspend fun getEnabledRules(): List<UserCategorizationRule> = withContext(ioDispatcher) {
        database.userCategorizationRuleQueries.getAllEnabled().executeAsList().map { entity ->
            UserCategorizationRule(
                id = entity.id,
                name = entity.name,
                conditions = json.decodeFromString(entity.conditions),
                category = Category.valueOf(entity.category),
                priority = entity.priority.toInt(),
                learnedFromExamples = entity.learnedFromExamples?.let { json.decodeFromString(it) },
                enabled = entity.enabled == 1L,
                createdAt = Instant.fromEpochMilliseconds(entity.createdAt),
            )
        }
    }

    override suspend fun deleteRule(id: String) = withContext(ioDispatcher) {
        database.userCategorizationRuleQueries.deleteRule(id)
    }

    override suspend fun updateRulePriority(id: String, priority: Int) = withContext(ioDispatcher) {
        database.userCategorizationRuleQueries.updatePriority(
            priority = priority.toLong(),
            id = id,
        )
    }
}
