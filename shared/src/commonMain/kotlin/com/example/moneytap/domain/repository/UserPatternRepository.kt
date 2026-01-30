package com.example.moneytap.domain.repository

import com.example.moneytap.domain.model.LearnedBankPattern
import com.example.moneytap.domain.model.TeachingExample

interface UserPatternRepository {
    suspend fun savePattern(pattern: LearnedBankPattern)
    suspend fun getAllPatterns(): List<LearnedBankPattern>
    suspend fun getPatternBySenderId(senderId: String): LearnedBankPattern?
    suspend fun updatePatternStats(id: String, successCount: Int, failCount: Int)
    suspend fun deletePattern(id: String)
    suspend fun saveTeachingExample(example: TeachingExample, patternId: String)
    suspend fun getExamplesForPattern(patternId: String): List<TeachingExample>
}
