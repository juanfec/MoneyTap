package com.example.moneytap.domain.usecase

import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.MatchType
import com.example.moneytap.domain.model.TransactionInfo
import com.example.moneytap.domain.model.TransactionType
import com.example.moneytap.domain.service.CategorizationEngine
import com.example.moneytap.testutil.FakeSmsRepository
import com.example.moneytap.testutil.FakeTransactionRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for verifying that categories with excludeFromSpending=true
 * are properly excluded from spending totals.
 */
class GetSpendingByCategoryExclusionTest {

    private lateinit var smsRepository: FakeSmsRepository
    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var parseSmsUseCase: ParseSmsTransactionsUseCase
    private lateinit var categorizeUseCase: CategorizeTransactionsUseCase
    private lateinit var getSpendingUseCase: GetSpendingByCategoryUseCase

    @BeforeTest
    fun setup() {
        smsRepository = FakeSmsRepository()
        transactionRepository = FakeTransactionRepository()
        parseSmsUseCase = ParseSmsTransactionsUseCase(smsRepository)
        categorizeUseCase = CategorizeTransactionsUseCase(CategorizationEngine())
        getSpendingUseCase = GetSpendingByCategoryUseCase(
            parseSmsTransactionsUseCase = parseSmsUseCase,
            categorizeTransactionsUseCase = categorizeUseCase,
            transactionRepository = transactionRepository,
        )
    }

    @Test
    fun `credit card payments should be excluded from total spending`() = runTest {
        // Given: Multiple transactions including credit card payment
        val transactions = listOf(
            createTransaction(1L, TransactionType.DEBIT, 50000.0, Category.GROCERIES),
            createTransaction(2L, TransactionType.DEBIT, 30000.0, Category.RESTAURANT),
            createTransaction(3L, TransactionType.DEBIT, 200000.0, Category.CREDIT_CARD_PAYMENT),
            createTransaction(4L, TransactionType.DEBIT, 15000.0, Category.GAS),
        )
        transactionRepository.setTransactions(transactions)

        // When: Getting spending summary
        val result = getSpendingUseCase.invoke(spendingOnly = true)

        // Then: Credit card payment should be excluded from total
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        
        // Total should only include groceries (50k) + restaurant (30k) + gas (15k) = 95k
        // Credit card payment (200k) should NOT be included
        assertEquals(95000.0, summary.totalSpending)
        
        // But credit card payment should still appear in the category breakdown
        assertEquals(4, summary.byCategory.size)
        assertTrue(summary.byCategory.containsKey(Category.CREDIT_CARD_PAYMENT))
        assertEquals(200000.0, summary.byCategory[Category.CREDIT_CARD_PAYMENT]?.totalAmount)
    }

    @Test
    fun `multiple credit card payments should all be excluded`() = runTest {
        // Given: Multiple credit card payments
        val transactions = listOf(
            createTransaction(1L, TransactionType.DEBIT, 100000.0, Category.GROCERIES),
            createTransaction(2L, TransactionType.DEBIT, 500000.0, Category.CREDIT_CARD_PAYMENT),
            createTransaction(3L, TransactionType.DEBIT, 300000.0, Category.CREDIT_CARD_PAYMENT),
            createTransaction(4L, TransactionType.DEBIT, 50000.0, Category.COFFEE),
        )
        transactionRepository.setTransactions(transactions)

        // When
        val result = getSpendingUseCase.invoke(spendingOnly = true)

        // Then: Only groceries (100k) + coffee (50k) = 150k
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(150000.0, summary.totalSpending)
        
        // Credit card payments category should show total of both payments
        assertEquals(800000.0, summary.byCategory[Category.CREDIT_CARD_PAYMENT]?.totalAmount)
    }

    @Test
    fun `spending totals work correctly with mixed transaction types`() = runTest {
        // Given: Mix of debits, credits, and credit card payments
        val transactions = listOf(
            createTransaction(1L, TransactionType.DEBIT, 100000.0, Category.GROCERIES),
            createTransaction(2L, TransactionType.CREDIT, 200000.0, Category.UNCATEGORIZED), // Income
            createTransaction(3L, TransactionType.DEBIT, 400000.0, Category.CREDIT_CARD_PAYMENT),
            createTransaction(4L, TransactionType.WITHDRAWAL, 50000.0, Category.GAS),
            createTransaction(5L, TransactionType.DEBIT, 30000.0, Category.COFFEE),
        )
        transactionRepository.setTransactions(transactions)

        // When: Getting spending only
        val result = getSpendingUseCase.invoke(spendingOnly = true)

        // Then: Only count DEBIT/WITHDRAWAL/TRANSFER excluding credit card payment
        // Groceries (100k) + Gas (50k) + Coffee (30k) = 180k
        // Credit card payment (400k) and Income (200k) should not be included
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(180000.0, summary.totalSpending)
    }

    @Test
    fun `transfer transactions should be counted as expenses`() = runTest {
        // Given: Mix including TRANSFER transactions
        val transactions = listOf(
            createTransaction(1L, TransactionType.DEBIT, 100000.0, Category.GROCERIES),
            createTransaction(2L, TransactionType.TRANSFER, 50000.0, Category.UNCATEGORIZED),
            createTransaction(3L, TransactionType.TRANSFER, 75000.0, Category.UNCATEGORIZED),
            createTransaction(4L, TransactionType.DEBIT, 30000.0, Category.COFFEE),
        )
        transactionRepository.setTransactions(transactions)

        // When
        val result = getSpendingUseCase.invoke(spendingOnly = true)

        // Then: TRANSFER should be counted as expenses
        // Groceries (100k) + Transfer (50k) + Transfer (75k) + Coffee (30k) = 255k
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(255000.0, summary.totalSpending)
    }

    @Test
    fun `all transactions with excludeFromSpending should be filtered`() = runTest {
        // Given: Only transactions that should be excluded
        val transactions = listOf(
            createTransaction(1L, TransactionType.DEBIT, 500000.0, Category.CREDIT_CARD_PAYMENT),
            createTransaction(2L, TransactionType.DEBIT, 300000.0, Category.CREDIT_CARD_PAYMENT),
        )
        transactionRepository.setTransactions(transactions)

        // When
        val result = getSpendingUseCase.invoke(spendingOnly = true)

        // Then: Total spending should be 0
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(0.0, summary.totalSpending)
        
        // But categories should still appear
        assertEquals(1, summary.byCategory.size)
        assertEquals(800000.0, summary.byCategory[Category.CREDIT_CARD_PAYMENT]?.totalAmount)
    }

    @Test
    fun `verify CREDIT_CARD_PAYMENT category has excludeFromSpending flag`() {
        // This test documents the expected behavior
        assertTrue(Category.CREDIT_CARD_PAYMENT.excludeFromSpending)
    }

    @Test
    fun `verify normal categories do not have excludeFromSpending flag`() {
        // Regular categories should count toward spending
        assertEquals(false, Category.GROCERIES.excludeFromSpending)
        assertEquals(false, Category.RESTAURANT.excludeFromSpending)
        assertEquals(false, Category.GAS.excludeFromSpending)
        assertEquals(false, Category.UNCATEGORIZED.excludeFromSpending)
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
