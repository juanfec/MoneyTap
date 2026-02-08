package com.example.moneytap.domain.usecase

import com.example.moneytap.domain.model.*
import com.example.moneytap.testutil.FakeTransactionRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test for the complete manual categorization flow.
 * Tests the interaction between UpdateTransactionCategoryUseCase and TransactionRepository.
 */
class ManualCategorizationIntegrationTest {

    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var updateCategoryUseCase: UpdateTransactionCategoryUseCase

    @BeforeTest
    fun setup() {
        transactionRepository = FakeTransactionRepository()
        updateCategoryUseCase = UpdateTransactionCategoryUseCase(transactionRepository)
    }

    @Test
    fun `full flow - user changes category and retrieves updated transaction`() = runTest {
        // Given: A transaction is stored in the repository
        val originalTransaction = createTransaction(
            smsId = 1L,
            category = Category.GROCERIES,
            matchType = MatchType.FUZZY,
            confidence = 0.85,
            userCorrected = false,
        )
        transactionRepository.setTransactions(listOf(originalTransaction))

        // When: User changes category to RESTAURANT
        val updateResult = updateCategoryUseCase(smsId = 1L, newCategory = Category.RESTAURANT)

        // Then: Update succeeds
        assertTrue(updateResult.isSuccess)

        // And: Retrieved transaction has new category
        val updatedTransaction = transactionRepository.getTransactionBySmsId(1L)
        assertNotNull(updatedTransaction)
        assertEquals(Category.RESTAURANT, updatedTransaction.category)
        assertEquals(MatchType.USER_RULE, updatedTransaction.matchType)
        assertEquals(1.0, updatedTransaction.confidence)
        assertTrue(updatedTransaction.userCorrected)
    }

    @Test
    fun `user can revert to original category by changing again`() = runTest {
        // Given: A transaction with GROCERIES
        val transaction = createTransaction(
            smsId = 1L,
            category = Category.GROCERIES,
            matchType = MatchType.EXACT,
            confidence = 0.95,
            userCorrected = false,
        )
        transactionRepository.setTransactions(listOf(transaction))

        // When: User changes to RESTAURANT then back to GROCERIES
        updateCategoryUseCase(smsId = 1L, newCategory = Category.RESTAURANT)
        updateCategoryUseCase(smsId = 1L, newCategory = Category.GROCERIES)

        // Then: Category is GROCERIES but still marked as user-corrected
        val updated = transactionRepository.getTransactionBySmsId(1L)
        assertNotNull(updated)
        assertEquals(Category.GROCERIES, updated.category)
        assertTrue(updated.userCorrected)
        assertEquals(MatchType.USER_RULE, updated.matchType)
    }

    @Test
    fun `user corrections persist in getAllTransactions`() = runTest {
        // Given: Three transactions
        val tx1 = createTransaction(smsId = 1L, category = Category.GROCERIES)
        val tx2 = createTransaction(smsId = 2L, category = Category.GAS)
        val tx3 = createTransaction(smsId = 3L, category = Category.RESTAURANT)
        transactionRepository.setTransactions(listOf(tx1, tx2, tx3))

        // When: User corrects only tx2
        updateCategoryUseCase(smsId = 2L, newCategory = Category.TAXI_RIDESHARE)

        // Then: All transactions are returned with correct flags
        val allTransactions = transactionRepository.getAllTransactions()
        assertEquals(3, allTransactions.size)

        val retrieved1 = allTransactions.find { it.transaction.smsId == 1L }
        val retrieved2 = allTransactions.find { it.transaction.smsId == 2L }
        val retrieved3 = allTransactions.find { it.transaction.smsId == 3L }

        assertNotNull(retrieved1)
        assertNotNull(retrieved2)
        assertNotNull(retrieved3)

        // Only tx2 is user-corrected
        assertEquals(false, retrieved1.userCorrected)
        assertEquals(true, retrieved2.userCorrected)
        assertEquals(false, retrieved3.userCorrected)

        // Only tx2 has new category
        assertEquals(Category.GROCERIES, retrieved1.category)
        assertEquals(Category.TAXI_RIDESHARE, retrieved2.category)
        assertEquals(Category.RESTAURANT, retrieved3.category)
    }

    @Test
    fun `user can change category of transaction with existing USER_RULE`() = runTest {
        // Given: A transaction already user-corrected to RESTAURANT
        val transaction = createTransaction(
            smsId = 1L,
            category = Category.RESTAURANT,
            matchType = MatchType.USER_RULE,
            confidence = 1.0,
            userCorrected = true,
        )
        transactionRepository.setTransactions(listOf(transaction))

        // When: User changes to COFFEE
        updateCategoryUseCase(smsId = 1L, newCategory = Category.COFFEE)

        // Then: Category updated to COFFEE
        val updated = transactionRepository.getTransactionBySmsId(1L)
        assertNotNull(updated)
        assertEquals(Category.COFFEE, updated.category)
        assertTrue(updated.userCorrected)
    }

    @Test
    fun `attempting to update non-existent transaction does not crash`() = runTest {
        // Given: Empty repository
        transactionRepository.setTransactions(emptyList())

        // When: Try to update non-existent transaction
        val result = updateCategoryUseCase(smsId = 999L, newCategory = Category.RESTAURANT)

        // Then: Operation succeeds (no-op)
        assertTrue(result.isSuccess)

        // And: No transaction is created
        val retrieved = transactionRepository.getTransactionBySmsId(999L)
        assertEquals(null, retrieved)
    }

    @Test
    fun `multiple users can change different transactions independently`() = runTest {
        // Given: Multiple transactions
        val tx1 = createTransaction(smsId = 1L, category = Category.GROCERIES)
        val tx2 = createTransaction(smsId = 2L, category = Category.GAS)
        val tx3 = createTransaction(smsId = 3L, category = Category.RESTAURANT)
        transactionRepository.setTransactions(listOf(tx1, tx2, tx3))

        // When: Change categories independently
        updateCategoryUseCase(smsId = 1L, newCategory = Category.COFFEE)
        updateCategoryUseCase(smsId = 3L, newCategory = Category.PHARMACY)

        // Then: Each transaction has correct category
        val updated1 = transactionRepository.getTransactionBySmsId(1L)
        val updated2 = transactionRepository.getTransactionBySmsId(2L)
        val updated3 = transactionRepository.getTransactionBySmsId(3L)

        assertNotNull(updated1)
        assertNotNull(updated2)
        assertNotNull(updated3)

        assertEquals(Category.COFFEE, updated1.category)
        assertEquals(Category.GAS, updated2.category)
        assertEquals(Category.PHARMACY, updated3.category)

        assertTrue(updated1.userCorrected)
        assertEquals(false, updated2.userCorrected)
        assertTrue(updated3.userCorrected)
    }

    private fun createTransaction(
        smsId: Long,
        category: Category,
        matchType: MatchType = MatchType.EXACT,
        confidence: Double = 0.95,
        userCorrected: Boolean = false,
    ): CategorizedTransaction {
        return CategorizedTransaction(
            transaction = TransactionInfo(
                smsId = smsId,
                type = TransactionType.DEBIT,
                amount = 100.0,
                bankName = "TestBank",
                timestamp = Clock.System.now(),
                rawMessage = "Test message",
            ),
            category = category,
            confidence = confidence,
            matchType = matchType,
            userCorrected = userCorrected,
        )
    }
}
