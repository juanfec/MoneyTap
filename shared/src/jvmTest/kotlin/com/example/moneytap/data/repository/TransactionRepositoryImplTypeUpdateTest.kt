package com.example.moneytap.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.moneytap.data.database.MoneyTapDatabase
import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.MatchType
import com.example.moneytap.domain.model.TransactionInfo
import com.example.moneytap.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TransactionRepositoryImplTypeUpdateTest {

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
    fun `updateTransactionType should change transaction type`() = runTest {
        // Given
        val smsId = 12345L
        val originalTransaction = createTestTransaction(smsId, TransactionType.DEBIT)
        repository.insertTransaction(originalTransaction)

        // When
        repository.updateTransactionType(smsId, TransactionType.CREDIT)

        // Then
        val updated = repository.getTransactionBySmsId(smsId)
        assertNotNull(updated)
        assertEquals(TransactionType.CREDIT, updated.transaction.type)
    }

    @Test
    fun `updateTransactionType should mark transaction as user corrected`() = runTest {
        // Given
        val smsId = 12345L
        val originalTransaction = createTestTransaction(smsId, TransactionType.DEBIT)
        repository.insertTransaction(originalTransaction)

        // When
        repository.updateTransactionType(smsId, TransactionType.CREDIT)

        // Then
        val updated = repository.getTransactionBySmsId(smsId)
        assertNotNull(updated)
        assertEquals(true, updated.userCorrected)
    }

    @Test
    fun `updateTransactionType should preserve other transaction fields`() = runTest {
        // Given
        val smsId = 12345L
        val originalTransaction = createTestTransaction(
            smsId = smsId,
            type = TransactionType.DEBIT,
            amount = 50000.0,
            merchant = "Test Merchant",
            category = Category.GROCERIES,
        )
        repository.insertTransaction(originalTransaction)

        // When
        repository.updateTransactionType(smsId, TransactionType.CREDIT)

        // Then
        val updated = repository.getTransactionBySmsId(smsId)
        assertNotNull(updated)
        assertEquals(TransactionType.CREDIT, updated.transaction.type)
        assertEquals(50000.0, updated.transaction.amount)
        assertEquals("Test Merchant", updated.transaction.merchant)
        assertEquals(Category.GROCERIES, updated.category)
    }

    @Test
    fun `updateTransactionType should work for all transaction types`() = runTest {
        // Given
        val baseId = 10000L
        TransactionType.entries.forEachIndexed { index, originalType ->
            val smsId = baseId + index
            val transaction = createTestTransaction(smsId, originalType)
            repository.insertTransaction(transaction)

            // When - Change to a different type
            val newType = TransactionType.entries[(index + 1) % TransactionType.entries.size]
            repository.updateTransactionType(smsId, newType)

            // Then
            val updated = repository.getTransactionBySmsId(smsId)
            assertNotNull(updated)
            assertEquals(newType, updated.transaction.type)
        }
    }

    private fun createTestTransaction(
        smsId: Long,
        type: TransactionType,
        amount: Double = 100.0,
        merchant: String? = null,
        category: Category = Category.UNCATEGORIZED,
    ): CategorizedTransaction {
        return CategorizedTransaction(
            transaction = TransactionInfo(
                smsId = smsId,
                type = type,
                amount = amount,
                merchant = merchant,
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
