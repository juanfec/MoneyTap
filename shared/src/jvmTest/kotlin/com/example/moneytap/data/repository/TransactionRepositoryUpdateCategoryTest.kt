package com.example.moneytap.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.moneytap.data.database.MoneyTapDatabase
import com.example.moneytap.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransactionRepositoryUpdateCategoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: MoneyTapDatabase
    private lateinit var repository: TransactionRepositoryImpl

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MoneyTapDatabase.Schema.create(driver)
        database = MoneyTapDatabase(driver)
        repository = TransactionRepositoryImpl(database)
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    @Test
    fun `updateTransactionCategory should update category in database`() = runTest {
        // Given: A transaction with GROCERIES category
        val transaction = createTransaction(
            smsId = 1L,
            category = Category.GROCERIES,
            matchType = MatchType.EXACT,
            confidence = 0.95,
            userCorrected = false,
        )
        repository.insertTransaction(transaction)

        // When: Update category to RESTAURANT
        repository.updateTransactionCategory(smsId = 1L, newCategory = Category.RESTAURANT)

        // Then: Category is updated in database
        val updated = repository.getTransactionBySmsId(1L)
        assertNotNull(updated)
        assertEquals(Category.RESTAURANT, updated.category)
    }

    @Test
    fun `updateTransactionCategory should set matchType to USER_RULE`() = runTest {
        // Given: A transaction with FUZZY matchType
        val transaction = createTransaction(
            smsId = 1L,
            category = Category.GROCERIES,
            matchType = MatchType.FUZZY,
            confidence = 0.85,
            userCorrected = false,
        )
        repository.insertTransaction(transaction)

        // When: Update category
        repository.updateTransactionCategory(smsId = 1L, newCategory = Category.RESTAURANT)

        // Then: MatchType is USER_RULE
        val updated = repository.getTransactionBySmsId(1L)
        assertNotNull(updated)
        assertEquals(MatchType.USER_RULE, updated.matchType)
    }

    @Test
    fun `updateTransactionCategory should set confidence to 1_0`() = runTest {
        // Given: A transaction with low confidence
        val transaction = createTransaction(
            smsId = 1L,
            category = Category.GROCERIES,
            matchType = MatchType.KEYWORD,
            confidence = 0.70,
            userCorrected = false,
        )
        repository.insertTransaction(transaction)

        // When: Update category
        repository.updateTransactionCategory(smsId = 1L, newCategory = Category.RESTAURANT)

        // Then: Confidence is 1.0
        val updated = repository.getTransactionBySmsId(1L)
        assertNotNull(updated)
        assertEquals(1.0, updated.confidence)
    }

    @Test
    fun `updateTransactionCategory should set userCorrected to true`() = runTest {
        // Given: A transaction not user-corrected
        val transaction = createTransaction(
            smsId = 1L,
            category = Category.GROCERIES,
            matchType = MatchType.EXACT,
            confidence = 0.95,
            userCorrected = false,
        )
        repository.insertTransaction(transaction)

        // When: Update category
        repository.updateTransactionCategory(smsId = 1L, newCategory = Category.RESTAURANT)

        // Then: userCorrected is true
        val updated = repository.getTransactionBySmsId(1L)
        assertNotNull(updated)
        assertTrue(updated.userCorrected)
    }

    @Test
    fun `getTransactionBySmsId should return null for non-existent transaction`() = runTest {
        // Given: Empty database
        // When: Query for non-existent transaction
        val result = repository.getTransactionBySmsId(999L)

        // Then: Returns null
        assertEquals(null, result)
    }

    @Test
    fun `updateTransactionCategory should allow changing category multiple times`() = runTest {
        // Given: A transaction
        val transaction = createTransaction(
            smsId = 1L,
            category = Category.GROCERIES,
            matchType = MatchType.EXACT,
            confidence = 0.95,
            userCorrected = false,
        )
        repository.insertTransaction(transaction)

        // When: Change category twice
        repository.updateTransactionCategory(smsId = 1L, newCategory = Category.RESTAURANT)
        repository.updateTransactionCategory(smsId = 1L, newCategory = Category.COFFEE)

        // Then: Final category is COFFEE
        val updated = repository.getTransactionBySmsId(1L)
        assertNotNull(updated)
        assertEquals(Category.COFFEE, updated.category)
        assertTrue(updated.userCorrected)
        assertEquals(MatchType.USER_RULE, updated.matchType)
        assertEquals(1.0, updated.confidence)
    }

    @Test
    fun `updateTransactionCategory should persist across getAllTransactions`() = runTest {
        // Given: A transaction
        val transaction = createTransaction(
            smsId = 1L,
            category = Category.GROCERIES,
            matchType = MatchType.EXACT,
            confidence = 0.95,
            userCorrected = false,
        )
        repository.insertTransaction(transaction)

        // When: Update category
        repository.updateTransactionCategory(smsId = 1L, newCategory = Category.RESTAURANT)

        // Then: Change is visible in getAllTransactions
        val allTransactions = repository.getAllTransactions()
        assertEquals(1, allTransactions.size)
        assertEquals(Category.RESTAURANT, allTransactions[0].category)
        assertTrue(allTransactions[0].userCorrected)
    }

    @Test
    fun `updateTransactionCategory should not affect other transactions`() = runTest {
        // Given: Two transactions
        val transaction1 = createTransaction(
            smsId = 1L,
            category = Category.GROCERIES,
            matchType = MatchType.EXACT,
            confidence = 0.95,
            userCorrected = false,
        )
        val transaction2 = createTransaction(
            smsId = 2L,
            category = Category.GAS,
            matchType = MatchType.EXACT,
            confidence = 0.90,
            userCorrected = false,
        )
        repository.insertTransaction(transaction1)
        repository.insertTransaction(transaction2)

        // When: Update only first transaction
        repository.updateTransactionCategory(smsId = 1L, newCategory = Category.RESTAURANT)

        // Then: Only first transaction is updated
        val updated1 = repository.getTransactionBySmsId(1L)
        val updated2 = repository.getTransactionBySmsId(2L)

        assertNotNull(updated1)
        assertNotNull(updated2)

        assertEquals(Category.RESTAURANT, updated1.category)
        assertTrue(updated1.userCorrected)

        assertEquals(Category.GAS, updated2.category)
        assertEquals(false, updated2.userCorrected)
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
