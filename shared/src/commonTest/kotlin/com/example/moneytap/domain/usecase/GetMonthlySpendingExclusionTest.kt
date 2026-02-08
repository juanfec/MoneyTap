package com.example.moneytap.domain.usecase

import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.MatchType
import com.example.moneytap.domain.model.TransactionInfo
import com.example.moneytap.domain.model.TransactionType
import com.example.moneytap.domain.model.YearMonth
import com.example.moneytap.testutil.FakeTransactionRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for verifying that categories with excludeFromSpending=true
 * are properly excluded from monthly spending calculations.
 */
class GetMonthlySpendingExclusionTest {

    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var getMonthlySpendingUseCase: GetMonthlySpendingUseCase

    @BeforeTest
    fun setup() {
        transactionRepository = FakeTransactionRepository()
        getMonthlySpendingUseCase = GetMonthlySpendingUseCase(transactionRepository)
    }

    @Test
    fun `credit card payments should be excluded from monthly expenses`() = runTest {
        // Given: Transactions in February including credit card payment
        val now = Clock.System.now()
        val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val month = YearMonth(String.format("%04d-%02d", localDate.year, localDate.monthNumber))

        val transactions = listOf(
            createTransaction(1L, TransactionType.DEBIT, 80000.0, Category.GROCERIES),
            createTransaction(2L, TransactionType.DEBIT, 40000.0, Category.RESTAURANT),
            createTransaction(3L, TransactionType.DEBIT, 500000.0, Category.CREDIT_CARD_PAYMENT),
            createTransaction(4L, TransactionType.WITHDRAWAL, 20000.0, Category.GAS),
        )
        transactionRepository.setTransactions(transactions)

        // When
        val result = getMonthlySpendingUseCase(month)

        // Then: Total expenses should exclude credit card payment
        // Groceries (80k) + Restaurant (40k) + Gas (20k) = 140k
        // Credit card payment (500k) should NOT be included
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(140000.0, summary.totalExpenses)
        
        // But credit card payment should still appear in categories
        assertTrue(summary.byCategory.containsKey(Category.CREDIT_CARD_PAYMENT))
        assertEquals(500000.0, summary.byCategory[Category.CREDIT_CARD_PAYMENT]?.totalAmount)
    }

    @Test
    fun `credit card payments should be excluded from monthly income`() = runTest {
        // Given: Income transactions including one marked as credit card payment
        val now = Clock.System.now()
        val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val month = YearMonth(String.format("%04d-%02d", localDate.year, localDate.monthNumber))

        val transactions = listOf(
            createTransaction(1L, TransactionType.CREDIT, 1000000.0, Category.UNCATEGORIZED), // Salary
            createTransaction(2L, TransactionType.CREDIT, 500000.0, Category.CREDIT_CARD_PAYMENT), // Refund
            createTransaction(3L, TransactionType.DEBIT, 100000.0, Category.GROCERIES),
        )
        transactionRepository.setTransactions(transactions)

        // When
        val result = getMonthlySpendingUseCase(month)

        // Then: Total income should exclude credit card payment category
        // Only uncategorized income (1M) should count
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(1000000.0, summary.totalIncome)
        assertEquals(100000.0, summary.totalExpenses)
    }

    @Test
    fun `monthly balance calculation with credit card payments`() = runTest {
        // Given: Mix of income, expenses, and credit card payments
        val now = Clock.System.now()
        val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val month = YearMonth(String.format("%04d-%02d", localDate.year, localDate.monthNumber))

        val transactions = listOf(
            // Income
            createTransaction(1L, TransactionType.CREDIT, 2000000.0, Category.UNCATEGORIZED),
            // Regular expenses
            createTransaction(2L, TransactionType.DEBIT, 300000.0, Category.GROCERIES),
            createTransaction(3L, TransactionType.DEBIT, 200000.0, Category.RESTAURANT),
            createTransaction(4L, TransactionType.DEBIT, 100000.0, Category.UTILITIES),
            // Credit card payment (should not affect balance)
            createTransaction(5L, TransactionType.DEBIT, 600000.0, Category.CREDIT_CARD_PAYMENT),
        )
        transactionRepository.setTransactions(transactions)

        // When
        val result = getMonthlySpendingUseCase(month)

        // Then: Balance should be Income - Expenses (excluding credit card payment)
        // Balance = 2,000,000 - (300k + 200k + 100k) = 2,000,000 - 600,000 = 1,400,000
        // Credit card payment should NOT affect the calculation
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(2000000.0, summary.totalIncome)
        assertEquals(600000.0, summary.totalExpenses)
        assertEquals(1400000.0, summary.balance)
    }

    @Test
    fun `multiple credit card payments in same month`() = runTest {
        // Given: Multiple credit card payments
        val now = Clock.System.now()
        val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val month = YearMonth(String.format("%04d-%02d", localDate.year, localDate.monthNumber))

        val transactions = listOf(
            createTransaction(1L, TransactionType.DEBIT, 150000.0, Category.GROCERIES),
            createTransaction(2L, TransactionType.DEBIT, 400000.0, Category.CREDIT_CARD_PAYMENT),
            createTransaction(3L, TransactionType.DEBIT, 300000.0, Category.CREDIT_CARD_PAYMENT),
            createTransaction(4L, TransactionType.DEBIT, 50000.0, Category.COFFEE),
        )
        transactionRepository.setTransactions(transactions)

        // When
        val result = getMonthlySpendingUseCase(month)

        // Then: Only regular spending counts
        // Groceries (150k) + Coffee (50k) = 200k
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(200000.0, summary.totalExpenses)
        
        // But credit card payments should show combined total
        assertEquals(700000.0, summary.byCategory[Category.CREDIT_CARD_PAYMENT]?.totalAmount)
    }

    @Test
    fun `monthly summary with only excluded categories`() = runTest {
        // Given: Only credit card payments in the month
        val now = Clock.System.now()
        val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val month = YearMonth(String.format("%04d-%02d", localDate.year, localDate.monthNumber))

        val transactions = listOf(
            createTransaction(1L, TransactionType.DEBIT, 1000000.0, Category.CREDIT_CARD_PAYMENT),
            createTransaction(2L, TransactionType.DEBIT, 500000.0, Category.CREDIT_CARD_PAYMENT),
        )
        transactionRepository.setTransactions(transactions)

        // When
        val result = getMonthlySpendingUseCase(month)

        // Then: Both income and expenses should be 0
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(0.0, summary.totalIncome)
        assertEquals(0.0, summary.totalExpenses)
        assertEquals(0.0, summary.balance)
        
        // But transactions should still appear in categories
        assertEquals(1, summary.byCategory.size)
        assertEquals(1500000.0, summary.byCategory[Category.CREDIT_CARD_PAYMENT]?.totalAmount)
    }

    @Test
    fun `transfer transactions should be counted as expenses in monthly summary`() = runTest {
        // Given: Mix including TRANSFER transactions
        val now = Clock.System.now()
        val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val month = YearMonth(String.format("%04d-%02d", localDate.year, localDate.monthNumber))

        val transactions = listOf(
            createTransaction(1L, TransactionType.CREDIT, 1000000.0, Category.UNCATEGORIZED), // Income
            createTransaction(2L, TransactionType.DEBIT, 100000.0, Category.GROCERIES),
            createTransaction(3L, TransactionType.TRANSFER, 50000.0, Category.UNCATEGORIZED),
            createTransaction(4L, TransactionType.TRANSFER, 75000.0, Category.UNCATEGORIZED),
        )
        transactionRepository.setTransactions(transactions)

        // When
        val result = getMonthlySpendingUseCase(month)

        // Then: TRANSFER should be counted as expenses
        // Income: 1,000,000
        // Expenses: 100,000 (Groceries) + 50,000 (Transfer) + 75,000 (Transfer) = 225,000
        // Balance: 1,000,000 - 225,000 = 775,000
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(1000000.0, summary.totalIncome)
        assertEquals(225000.0, summary.totalExpenses)
        assertEquals(775000.0, summary.balance)
    }

    private fun createTransaction(
        smsId: Long,
        type: TransactionType,
        amount: Double,
        category: Category,
    ): CategorizedTransaction {
        return CategorizedTransaction(
            transaction = TransactionInfo(
                smsId = smsId,
                type = type,
                amount = amount,
                merchant = "Test Merchant",
                bankName = "TestBank",
                timestamp = Clock.System.now(),
                rawMessage = "Test transaction",
            ),
            category = category,
            confidence = 0.9,
            matchType = MatchType.EXACT,
            userCorrected = false,
        )
    }
}
