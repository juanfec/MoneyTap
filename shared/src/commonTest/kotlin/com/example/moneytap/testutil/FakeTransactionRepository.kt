package com.example.moneytap.testutil

import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.MonthlyTotal
import com.example.moneytap.domain.repository.TransactionRepository
import kotlinx.datetime.Instant

class FakeTransactionRepository : TransactionRepository {

    private val transactions = mutableListOf<CategorizedTransaction>()

    var insertCallCount = 0
        private set

    override suspend fun insertTransaction(transaction: CategorizedTransaction) {
        insertCallCount++
        val existing = transactions.indexOfFirst {
            it.transaction.smsId == transaction.transaction.smsId
        }
        if (existing >= 0) {
            transactions[existing] = transaction
        } else {
            transactions.add(transaction)
        }
    }

    override suspend fun insertTransactions(transactions: List<CategorizedTransaction>) {

        transactions.forEach { transaction ->
            val existing = this.transactions.indexOfFirst {
                it.transaction.smsId == transaction.transaction.smsId
            }
            if (existing >= 0) {
                this.transactions[existing] = transaction
            } else {
                this.transactions.add(transaction)
                insertCallCount++
            }
        }
    }

    override suspend fun getAllTransactions(): List<CategorizedTransaction> =
        transactions.sortedByDescending { it.transaction.timestamp }

    override suspend fun getTransactionsByDateRange(
        startDate: Instant,
        endDate: Instant,
    ): List<CategorizedTransaction> =
        transactions.filter {
            val ts = it.transaction.timestamp
            ts >= startDate && ts <= endDate
        }.sortedByDescending { it.transaction.timestamp }

    override suspend fun getStoredSmsIds(): Set<Long> =
        transactions.map { it.transaction.smsId }.toSet()

    override suspend fun getMonthlyTotals(): List<MonthlyTotal> = emptyList()

    override suspend fun getTransactionCount(): Long = transactions.size.toLong()

    override suspend fun deleteAllTransactions() {
        transactions.clear()
    }
}
