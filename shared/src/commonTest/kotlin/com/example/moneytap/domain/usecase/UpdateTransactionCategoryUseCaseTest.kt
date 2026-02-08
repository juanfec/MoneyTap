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

class UpdateTransactionCategoryUseCaseTest {

    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var useCase: UpdateTransactionCategoryUseCase

    @BeforeTest
    fun setup() {
        transactionRepository = FakeTransactionRepository()
        useCase = UpdateTransactionCategoryUseCase(transactionRepository)
    }

    @Test
    fun `should successfully update category`() = runTest {
        // Given: A transaction with GROCERIES category
        val transaction = createTransaction(
            smsId = 1L,
            category = Category.GROCERIES,
            matchType = MatchType.EXACT,
            confidence = 0.95,
            userCorrected = false,
        )
        transactionRepository.setTransactions(listOf(transaction))

        // When: Update to RESTAURANT
        val result = useCase(smsId = 1L, newCategory = Category.RESTAURANT)

        // Then: Success
        assertTrue(result.isSuccess)

        // And: Category is updated
        val updated = transactionRepository.getTransactionBySmsId(1L)
        assertNotNull(updated)
        assertEquals(Category.RESTAURANT, updated.category)
    }

    @Test
    fun `should set matchType to USER_RULE when updated`() = runTest {
        // Given: A transaction with FUZZY matchType
        val transaction = createTransaction(
            smsId = 1L,
            category = Category.GROCERIES,
            matchType = MatchType.FUZZY,
            confidence = 0.85,
            userCorrected = false,
        )
        transactionRepository.setTransactions(listOf(transaction))

        // When: Update category
        useCase(smsId = 1L, newCategory = Category.RESTAURANT)

        // Then: MatchType is USER_RULE
        val updated = transactionRepository.getTransactionBySmsId(1L)
        assertNotNull(updated)
        assertEquals(MatchType.USER_RULE, updated.matchType)
    }

    @Test
    fun `should set confidence to 1_0 when updated`() = runTest {
        // Given: A transaction with low confidence
        val transaction = createTransaction(
            smsId = 1L,
            category = Category.GROCERIES,
            matchType = MatchType.KEYWORD,
            confidence = 0.70,
            userCorrected = false,
        )
        transactionRepository.setTransactions(listOf(transaction))

        // When: Update category
        useCase(smsId = 1L, newCategory = Category.RESTAURANT)

        // Then: Confidence is 1.0
        val updated = transactionRepository.getTransactionBySmsId(1L)
        assertNotNull(updated)
        assertEquals(1.0, updated.confidence)
    }

    @Test
    fun `should set userCorrected to true when updated`() = runTest {
        // Given: A transaction not user-corrected
        val transaction = createTransaction(
            smsId = 1L,
            category = Category.GROCERIES,
            matchType = MatchType.EXACT,
            confidence = 0.95,
            userCorrected = false,
        )
        transactionRepository.setTransactions(listOf(transaction))

        // When: Update category
        useCase(smsId = 1L, newCategory = Category.RESTAURANT)

        // Then: userCorrected is true
        val updated = transactionRepository.getTransactionBySmsId(1L)
        assertNotNull(updated)
        assertTrue(updated.userCorrected)
    }

    @Test
    fun `should handle transaction not found gracefully`() = runTest {
        // Given: Empty repository
        transactionRepository.setTransactions(emptyList())

        // When: Try to update non-existent transaction
        val result = useCase(smsId = 999L, newCategory = Category.RESTAURANT)

        // Then: Success (no-op, doesn't throw)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should allow changing category multiple times`() = runTest {
        // Given: A transaction
        val transaction = createTransaction(
            smsId = 1L,
            category = Category.GROCERIES,
            matchType = MatchType.EXACT,
            confidence = 0.95,
            userCorrected = false,
        )
        transactionRepository.setTransactions(listOf(transaction))

        // When: Change category twice
        useCase(smsId = 1L, newCategory = Category.RESTAURANT)
        useCase(smsId = 1L, newCategory = Category.COFFEE)

        // Then: Final category is COFFEE
        val updated = transactionRepository.getTransactionBySmsId(1L)
        assertNotNull(updated)
        assertEquals(Category.COFFEE, updated.category)
        assertTrue(updated.userCorrected)
    }

    private fun createTransaction(
        smsId: Long,
        category: Category,
        matchType: MatchType,
        confidence: Double,
        userCorrected: Boolean,
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
