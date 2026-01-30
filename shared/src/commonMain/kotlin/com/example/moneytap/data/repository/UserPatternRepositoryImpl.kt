package com.example.moneytap.data.repository

import com.example.moneytap.data.database.MoneyTapDatabase
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.FieldSelection
import com.example.moneytap.domain.model.InferredPattern
import com.example.moneytap.domain.model.LearnedBankPattern
import com.example.moneytap.domain.model.TeachingExample
import com.example.moneytap.domain.repository.UserPatternRepository
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

class UserPatternRepositoryImpl(
    private val database: MoneyTapDatabase,
    private val ioDispatcher: CoroutineContext,
) : UserPatternRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun savePattern(pattern: LearnedBankPattern) = withContext(ioDispatcher) {
        database.userBankPatternQueries.insertPattern(
            id = pattern.id,
            bankName = pattern.bankName,
            senderIds = json.encodeToString(pattern.senderIds),
            inferredPattern = json.encodeToString(pattern.inferredPattern),
            defaultCategory = pattern.defaultCategory?.name,
            enabled = if (pattern.enabled) 1L else 0L,
            successCount = pattern.successCount.toLong(),
            failCount = pattern.failCount.toLong(),
            createdAt = pattern.createdAt.toEpochMilliseconds(),
            updatedAt = pattern.updatedAt.toEpochMilliseconds(),
        )
    }

    override suspend fun getAllPatterns(): List<LearnedBankPattern> = withContext(ioDispatcher) {
        database.userBankPatternQueries.getAll().executeAsList().map { entity ->
            LearnedBankPattern(
                id = entity.id,
                bankName = entity.bankName,
                senderIds = json.decodeFromString(entity.senderIds),
                examples = getExamplesForPattern(entity.id),
                inferredPattern = json.decodeFromString(entity.inferredPattern),
                defaultCategory = entity.defaultCategory?.let { Category.valueOf(it) },
                enabled = entity.enabled == 1L,
                successCount = entity.successCount.toInt(),
                failCount = entity.failCount.toInt(),
                createdAt = Instant.fromEpochMilliseconds(entity.createdAt),
                updatedAt = Instant.fromEpochMilliseconds(entity.updatedAt),
            )
        }
    }

    override suspend fun getPatternBySenderId(senderId: String): LearnedBankPattern? =
        withContext(ioDispatcher) {
            database.userBankPatternQueries.getBySenderId(senderId).executeAsOneOrNull()?.let { entity ->
                LearnedBankPattern(
                    id = entity.id,
                    bankName = entity.bankName,
                    senderIds = json.decodeFromString(entity.senderIds),
                    examples = getExamplesForPattern(entity.id),
                    inferredPattern = json.decodeFromString(entity.inferredPattern),
                    defaultCategory = entity.defaultCategory?.let { Category.valueOf(it) },
                    enabled = entity.enabled == 1L,
                    successCount = entity.successCount.toInt(),
                    failCount = entity.failCount.toInt(),
                    createdAt = Instant.fromEpochMilliseconds(entity.createdAt),
                    updatedAt = Instant.fromEpochMilliseconds(entity.updatedAt),
                )
            }
        }

    override suspend fun updatePatternStats(id: String, successCount: Int, failCount: Int) =
        withContext(ioDispatcher) {
            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            database.userBankPatternQueries.updateStats(
                successCount = successCount.toLong(),
                failCount = failCount.toLong(),
                updatedAt = now,
                id = id,
            )
        }

    override suspend fun deletePattern(id: String) = withContext(ioDispatcher) {
        database.userBankPatternQueries.deletePattern(id)
    }

    override suspend fun saveTeachingExample(example: TeachingExample, patternId: String) =
        withContext(ioDispatcher) {
            database.teachingExampleQueries.insertExample(
                id = example.id,
                bankPatternId = patternId,
                smsBody = example.smsBody,
                senderId = example.senderId,
                selections = json.encodeToString(example.selections),
                category = example.category?.name,
                createdAt = example.createdAt.toEpochMilliseconds(),
            )
        }

    override suspend fun getExamplesForPattern(patternId: String): List<TeachingExample> =
        withContext(ioDispatcher) {
            database.teachingExampleQueries.getByPatternId(patternId).executeAsList().map { entity ->
                TeachingExample(
                    id = entity.id,
                    smsBody = entity.smsBody,
                    senderId = entity.senderId,
                    selections = json.decodeFromString(entity.selections),
                    category = entity.category?.let { Category.valueOf(it) },
                    createdAt = Instant.fromEpochMilliseconds(entity.createdAt),
                )
            }
        }
}
