package com.example.moneytap.data.repository

import com.example.moneytap.data.database.MoneyTapDatabase
import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.MatchType
import com.example.moneytap.domain.model.MonthlyTotal
import com.example.moneytap.domain.model.TransactionInfo
import com.example.moneytap.domain.model.TransactionType
import com.example.moneytap.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

class TransactionRepositoryImpl(
    private val database: MoneyTapDatabase,
) : TransactionRepository {

    private val queries get() = database.transactionQueries

    override suspend fun insertTransaction(transaction: CategorizedTransaction) {
        withContext(Dispatchers.Default) {
            val txInfo = transaction.transaction

            queries.insertTransaction(
                smsId = txInfo.smsId,
                amount = txInfo.amount,
                type = txInfo.type.name,
                currency = txInfo.currency,
                balance = txInfo.balance,
                cardLast4 = txInfo.cardLast4,
                merchant = txInfo.merchant,
                description = txInfo.description,
                reference = txInfo.reference,
                bankName = txInfo.bankName,
                timestamp = txInfo.timestamp.toEpochMilliseconds(),
                rawMessage = txInfo.rawMessage,
                category = transaction.category.name,
                confidence = transaction.confidence,
                matchType = transaction.matchType.name,
                userCorrected = 0L,
            )
        }
    }

    override suspend fun insertTransactions(transactions: List<CategorizedTransaction>) {
        withContext(Dispatchers.Default) {
            queries.transaction {
                for (transaction in transactions) {
                    val txInfo = transaction.transaction

                    queries.insertTransaction(
                        smsId = txInfo.smsId,
                        amount = txInfo.amount,
                        type = txInfo.type.name,
                        currency = txInfo.currency,
                        balance = txInfo.balance,
                        cardLast4 = txInfo.cardLast4,
                        merchant = txInfo.merchant,
                        description = txInfo.description,
                        reference = txInfo.reference,
                        bankName = txInfo.bankName,
                        timestamp = txInfo.timestamp.toEpochMilliseconds(),
                        rawMessage = txInfo.rawMessage,
                        category = transaction.category.name,
                        confidence = transaction.confidence,
                        matchType = transaction.matchType.name,
                        userCorrected = 0L,
                    )
                }
            }
        }
    }

    override suspend fun getAllTransactions(): List<CategorizedTransaction> =
        withContext(Dispatchers.Default) {
            queries.getAllTransactions().executeAsList().map { entity ->
                mapEntityToDomain(entity)
            }
        }

    override suspend fun getTransactionsByDateRange(
        startDate: Instant,
        endDate: Instant,
    ): List<CategorizedTransaction> =
        withContext(Dispatchers.Default) {
            queries.getTransactionsByDateRange(
                startDate = startDate.toEpochMilliseconds(),
                endDate = endDate.toEpochMilliseconds(),
            ).executeAsList().map { entity ->
                mapEntityToDomain(entity)
            }
        }

    override suspend fun getStoredSmsIds(): Set<Long> =
        withContext(Dispatchers.Default) {
            queries.getAllSmsIds().executeAsList().toSet()
        }

    override suspend fun getMonthlyTotals(): List<MonthlyTotal> =
        withContext(Dispatchers.Default) {
            queries.getMonthlyTotals().executeAsList().map { row ->
                MonthlyTotal(
                    month = row.month.orEmpty(),
                    expenses = row.expenses ?: 0.0,
                    income = row.income ?: 0.0,
                )
            }
        }

    override suspend fun getTransactionCount(): Long =
        withContext(Dispatchers.Default) {
            queries.getTransactionCount().executeAsOne()
        }

    override suspend fun deleteAllTransactions() {
        withContext(Dispatchers.Default) {
            queries.deleteAllTransactions()
        }
    }

    override suspend fun getTransactionBySmsId(smsId: Long): CategorizedTransaction? =
        withContext(Dispatchers.Default) {
            queries.getTransactionBySmsId(smsId).executeAsOneOrNull()?.let { entity ->
                mapEntityToDomain(entity)
            }
        }

    override suspend fun updateTransactionCategory(smsId: Long, newCategory: Category) {
        withContext(Dispatchers.Default) {
            queries.updateCategory(
                category = newCategory.name,
                smsId = smsId,
            )
        }
    }

    override suspend fun updateTransactionType(smsId: Long, newType: TransactionType) {
        withContext(Dispatchers.Default) {
            queries.updateType(
                type = newType.name,
                smsId = smsId,
            )
        }
    }

    private fun mapEntityToDomain(
        entity: com.example.moneytap.data.database.TransactionEntity,
    ): CategorizedTransaction {
        val transactionInfo = TransactionInfo(
            smsId = entity.smsId,
            type = TransactionType.valueOf(entity.type),
            amount = entity.amount,
            currency = entity.currency,
            balance = entity.balance,
            cardLast4 = entity.cardLast4,
            merchant = entity.merchant,
            description = entity.description,
            reference = entity.reference,
            bankName = entity.bankName,
            timestamp = Instant.fromEpochMilliseconds(entity.timestamp),
            rawMessage = entity.rawMessage,
        )

        return CategorizedTransaction(
            transaction = transactionInfo,
            category = Category.entries.find { it.name == entity.category }
                ?: Category.UNCATEGORIZED,
            confidence = entity.confidence,
            matchType = MatchType.entries.find { it.name == entity.matchType }
                ?: MatchType.DEFAULT,
            userCorrected = entity.userCorrected != 0L,
        )
    }
}
