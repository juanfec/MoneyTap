package com.example.moneytap.domain.usecase

import com.example.moneytap.domain.model.*
import com.example.moneytap.testutil.FakeTransactionRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetMonthlySpendingUseCaseTest {

    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var useCase: GetMonthlySpendingUseCase

    @BeforeTest
    fun setup() {
        transactionRepository = FakeTransactionRepository()
        useCase = GetMonthlySpendingUseCase(transactionRepository)
    }

    @Test
    fun `should return correct totals for single month`() = runTest {
        val month = YearMonth("2025-01")
        val transactions = listOf(
            createTransaction(
                smsId = 1,
                amount = 100.0,
                type = TransactionType.DEBIT,
                timestamp = month.startOfMonth().plus(5.days),
                category = Category.GROCERIES,
            ),
            createTransaction(
                smsId = 2,
                amount = 50.0,
                type = TransactionType.WITHDRAWAL,
                timestamp = month.startOfMonth().plus(10.days),
                category = Category.RESTAURANT,
            ),
            createTransaction(
                smsId = 3,
                amount = 500.0,
                type = TransactionType.CREDIT,
                timestamp = month.startOfMonth().plus(1.days),
                category = Category.UNCATEGORIZED,
            ),
        )

        transactionRepository.setTransactions(transactions)

        val result = useCase(month).getOrThrow()

        assertEquals(500.0, result.totalIncome)
        assertEquals(150.0, result.totalExpenses)
        assertEquals(350.0, result.balance)
        assertEquals(3, result.transactionCount)
    }

    @Test
    fun `should correctly separate income vs expenses`() = runTest {
        val month = YearMonth("2025-01")
        val transactions = listOf(
            createTransaction(
                smsId = 1,
                amount = 100.0,
                type = TransactionType.CREDIT,
                timestamp = month.startOfMonth(),
                category = Category.UNCATEGORIZED,
            ),
            createTransaction(
                smsId = 2,
                amount = 200.0,
                type = TransactionType.CREDIT,
                timestamp = month.startOfMonth(),
                category = Category.UNCATEGORIZED,
            ),
            createTransaction(
                smsId = 3,
                amount = 50.0,
                type = TransactionType.DEBIT,
                timestamp = month.startOfMonth(),
                category = Category.GROCERIES,
            ),
            createTransaction(
                smsId = 4,
                amount = 30.0,
                type = TransactionType.WITHDRAWAL,
                timestamp = month.startOfMonth(),
                category = Category.RESTAURANT,
            ),
            createTransaction(
                smsId = 5,
                amount = 75.0,
                type = TransactionType.TRANSFER,
                timestamp = month.startOfMonth(),
                category = Category.UNCATEGORIZED,
            ),
        )

        transactionRepository.setTransactions(transactions)

        val result = useCase(month).getOrThrow()

        assertEquals(300.0, result.totalIncome) // CREDIT only
        assertEquals(155.0, result.totalExpenses) // DEBIT + WITHDRAWAL + TRANSFER
        assertEquals(145.0, result.balance)
    }

    @Test
    fun `should group by category correctly`() = runTest {
        val month = YearMonth("2025-01")
        val transactions = listOf(
            createTransaction(
                smsId = 1,
                amount = 100.0,
                type = TransactionType.DEBIT,
                timestamp = month.startOfMonth(),
                category = Category.GROCERIES,
            ),
            createTransaction(
                smsId = 2,
                amount = 50.0,
                type = TransactionType.DEBIT,
                timestamp = month.startOfMonth(),
                category = Category.GROCERIES,
            ),
            createTransaction(
                smsId = 3,
                amount = 75.0,
                type = TransactionType.DEBIT,
                timestamp = month.startOfMonth(),
                category = Category.RESTAURANT,
            ),
        )

        transactionRepository.setTransactions(transactions)

        val result = useCase(month).getOrThrow()

        assertEquals(2, result.byCategory.size)
        assertEquals(150.0, result.byCategory[Category.GROCERIES]?.totalAmount)
        assertEquals(2, result.byCategory[Category.GROCERIES]?.transactionCount)
        assertEquals(75.0, result.byCategory[Category.RESTAURANT]?.totalAmount)
        assertEquals(1, result.byCategory[Category.RESTAURANT]?.transactionCount)
    }

    @Test
    fun `should return empty summary for month with no transactions`() = runTest {
        val month = YearMonth("2025-01")

        transactionRepository.setTransactions(emptyList())

        val result = useCase(month).getOrThrow()

        assertEquals(0.0, result.totalIncome)
        assertEquals(0.0, result.totalExpenses)
        assertEquals(0.0, result.balance)
        assertEquals(0, result.transactionCount)
        assertTrue(result.byCategory.isEmpty())
    }

    @Test
    fun `should only include transactions from selected month`() = runTest {
        val january = YearMonth("2025-01")
        val february = YearMonth("2025-02")

        val transactions = listOf(
            createTransaction(
                smsId = 1,
                amount = 100.0,
                type = TransactionType.DEBIT,
                timestamp = january.startOfMonth().plus(5.days),
                category = Category.GROCERIES,
            ),
            createTransaction(
                smsId = 2,
                amount = 200.0,
                type = TransactionType.DEBIT,
                timestamp = february.startOfMonth().plus(5.days),
                category = Category.GROCERIES,
            ),
        )

        transactionRepository.setTransactions(transactions)

        val result = useCase(january).getOrThrow()

        assertEquals(1, result.transactionCount)
        assertEquals(100.0, result.totalExpenses)
    }

    private fun createTransaction(
        smsId: Long,
        amount: Double,
        type: TransactionType,
        timestamp: Instant,
        category: Category,
    ): CategorizedTransaction {
        return CategorizedTransaction(
            transaction = TransactionInfo(
                smsId = smsId,
                type = type,
                amount = amount,
                bankName = "TestBank",
                timestamp = timestamp,
                rawMessage = "Test message",
            ),
            category = category,
            confidence = 0.9,
            matchType = MatchType.EXACT,
        )
    }
}
