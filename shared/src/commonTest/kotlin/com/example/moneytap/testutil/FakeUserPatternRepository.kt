package com.example.moneytap.testutil

import com.example.moneytap.domain.model.LearnedBankPattern
import com.example.moneytap.domain.model.TeachingExample
import com.example.moneytap.domain.repository.UserPatternRepository

class FakeUserPatternRepository : UserPatternRepository {

    private val patterns = mutableMapOf<String, LearnedBankPattern>()
    private val examples = mutableMapOf<String, MutableList<TeachingExample>>()

    var savePatternCallCount = 0
        private set

    override suspend fun savePattern(pattern: LearnedBankPattern) {
        savePatternCallCount++
        patterns[pattern.id] = pattern
    }

    override suspend fun getAllPatterns(): List<LearnedBankPattern> =
        patterns.values.toList()

    override suspend fun getPatternBySenderId(senderId: String): LearnedBankPattern? =
        patterns.values.firstOrNull { pattern ->
            pattern.senderIds.any { it.contains(senderId, ignoreCase = true) }
        }

    override suspend fun updatePatternStats(id: String, successCount: Int, failCount: Int) {
        patterns[id]?.let { pattern ->
            patterns[id] = pattern.copy(
                successCount = successCount,
                failCount = failCount,
                updatedAt = kotlinx.datetime.Clock.System.now(),
            )
        }
    }

    override suspend fun deletePattern(id: String) {
        patterns.remove(id)
        examples.remove(id)
    }

    override suspend fun saveTeachingExample(example: TeachingExample, patternId: String) {
        examples.getOrPut(patternId) { mutableListOf() }.add(example)
    }

    override suspend fun getExamplesForPattern(patternId: String): List<TeachingExample> =
        examples[patternId] ?: emptyList()

    fun clear() {
        patterns.clear()
        examples.clear()
        savePatternCallCount = 0
    }
}
