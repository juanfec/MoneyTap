package com.example.moneytap.domain.usecase

import com.example.moneytap.domain.model.TransactionType
import com.example.moneytap.domain.repository.TransactionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class UpdateTransactionTypeUseCaseTest {

    private class FakeTransactionRepository : TransactionRepository {
        var lastUpdatedSmsId: Long? = null
        var lastUpdatedType: TransactionType? = null
        var shouldThrowError = false

        override suspend fun updateTransactionType(smsId: Long, newType: TransactionType) {
            if (shouldThrowError) throw Exception("Test error")
            lastUpdatedSmsId = smsId
            lastUpdatedType = newType
        }

        // Stub implementations for other methods
        override suspend fun insertTransaction(transaction: com.example.moneytap.domain.model.CategorizedTransaction) {}
        override suspend fun insertTransactions(transactions: List<com.example.moneytap.domain.model.CategorizedTransaction>) {}
        override suspend fun getAllTransactions(): List<com.example.moneytap.domain.model.CategorizedTransaction> = emptyList()
        override suspend fun getTransactionsByDateRange(
            startDate: kotlinx.datetime.Instant,
            endDate: kotlinx.datetime.Instant
        ): List<com.example.moneytap.domain.model.CategorizedTransaction> = emptyList()
        override suspend fun getMonthlyTotals(): List<com.example.moneytap.domain.model.MonthlyTotal> = emptyList()
        override suspend fun getStoredSmsIds(): Set<Long> = emptySet()
        override suspend fun getTransactionCount(): Long = 0L
        override suspend fun deleteAllTransactions() {}
        override suspend fun getTransactionBySmsId(smsId: Long): com.example.moneytap.domain.model.CategorizedTransaction? = null
        override suspend fun updateTransactionCategory(smsId: Long, newCategory: com.example.moneytap.domain.model.Category) {}
    }

    @Test
    fun `invoke should update transaction type in repository`() = runTest {
        // Given
        val repository = FakeTransactionRepository()
        val useCase = UpdateTransactionTypeUseCase(repository)
        val smsId = 12345L
        val newType = TransactionType.CREDIT

        // When
        val result = useCase(smsId, newType)

        // Then
        assertTrue(result.isSuccess)
        assertTrue(repository.lastUpdatedSmsId == smsId)
        assertTrue(repository.lastUpdatedType == newType)
    }

    @Test
    fun `invoke should return failure when repository throws exception`() = runTest {
        // Given
        val repository = FakeTransactionRepository().apply {
            shouldThrowError = true
        }
        val useCase = UpdateTransactionTypeUseCase(repository)

        // When
        val result = useCase(12345L, TransactionType.DEBIT)

        // Then
        assertTrue(result.isFailure)
    }

    @Test
    fun `invoke should handle all transaction types`() = runTest {
        // Given
        val repository = FakeTransactionRepository()
        val useCase = UpdateTransactionTypeUseCase(repository)
        val smsId = 12345L

        // When & Then
        TransactionType.entries.forEach { type ->
            val result = useCase(smsId, type)
            assertTrue(result.isSuccess)
            assertTrue(repository.lastUpdatedType == type)
        }
    }
}
