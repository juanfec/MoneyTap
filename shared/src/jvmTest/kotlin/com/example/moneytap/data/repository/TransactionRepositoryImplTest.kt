package com.example.moneytap.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.moneytap.data.database.MoneyTapDatabase
import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.MatchType
import com.example.moneytap.domain.model.TransactionInfo
import com.example.moneytap.domain.model.TransactionType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionRepositoryImplTest {

    private lateinit var database: MoneyTapDatabase
    private lateinit var repository: TransactionRepositoryImpl

    private val testTimestamp = Instant.parse("2024-01-15T10:30:00Z")
    private val laterTimestamp = Instant.parse("2024-02-20T14:00:00Z")

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MoneyTapDatabase.Schema.create(driver)
        database = MoneyTapDatabase(driver)
        repository = TransactionRepositoryImpl(database)
    }

    private fun createCategorizedTransaction(
        smsId: Long = 1L,
        amount: Double = 50000.0,
        type: TransactionType = TransactionType.DEBIT,
        merchant: String? = "EXITO",
        category: Category = Category.GROCERIES,
        confidence: Double = 0.95,
        matchType: MatchType = MatchType.EXACT,
        timestamp: Instant = testTimestamp,
        currency: String = "COP",
        balance: Double? = 500000.0,
        cardLast4: String? = "1234",
        description: String? = null,
        reference: String? = null,
        bankName: String = "Bancolombia",
        rawMessage: String = "Compra por \$50.000",
    ) = CategorizedTransaction(
        transaction = TransactionInfo(
            smsId = smsId,
            type = type,
            amount = amount,
            currency = currency,
            balance = balance,
            cardLast4 = cardLast4,
            merchant = merchant,
            description = description,
            reference = reference,
            bankName = bankName,
            timestamp = timestamp,
            rawMessage = rawMessage,
        ),
        category = category,
        confidence = confidence,
        matchType = matchType,
    )

    // =================================
    // Insert & Retrieve
    // =================================

    @Test
    fun `insertTransaction stores and retrieves a single transaction`() = runTest {
        val transaction = createCategorizedTransaction()

        repository.insertTransaction(transaction)
        val result = repository.getAllTransactions()

        assertEquals(1, result.size)
        assertEquals(transaction.transaction.smsId, result[0].transaction.smsId)
        assertEquals(transaction.transaction.amount, result[0].transaction.amount)
        assertEquals(transaction.category, result[0].category)
    }

    @Test
    fun `insertTransactions stores multiple transactions`() = runTest {
        val transactions = listOf(
            createCategorizedTransaction(smsId = 1, merchant = "EXITO"),
            createCategorizedTransaction(smsId = 2, merchant = "RAPPI", category = Category.RESTAURANT),
            createCategorizedTransaction(smsId = 3, merchant = "JUAN VALDEZ", category = Category.COFFEE),
        )

        repository.insertTransactions(transactions)
        val result = repository.getAllTransactions()

        assertEquals(3, result.size)
    }

    // =================================
    // Field Mapping
    // =================================

    @Test
    fun `all fields are correctly persisted and retrieved`() = runTest {
        val transaction = createCategorizedTransaction(
            smsId = 42L,
            amount = 123456.78,
            type = TransactionType.CREDIT,
            merchant = "ALMACEN TEST",
            category = Category.GROCERIES,
            confidence = 0.85,
            matchType = MatchType.FUZZY,
            currency = "USD",
            balance = 999999.0,
            cardLast4 = "5678",
            description = "Test description",
            reference = "REF-001",
            bankName = "TestBank",
            rawMessage = "Raw SMS body here",
        )

        repository.insertTransaction(transaction)
        val result = repository.getAllTransactions().first()

        assertEquals(42L, result.transaction.smsId)
        assertEquals(123456.78, result.transaction.amount)
        assertEquals(TransactionType.CREDIT, result.transaction.type)
        assertEquals("ALMACEN TEST", result.transaction.merchant)
        assertEquals("USD", result.transaction.currency)
        assertEquals(999999.0, result.transaction.balance)
        assertEquals("5678", result.transaction.cardLast4)
        assertEquals("Test description", result.transaction.description)
        assertEquals("REF-001", result.transaction.reference)
        assertEquals("TestBank", result.transaction.bankName)
        assertEquals("Raw SMS body here", result.transaction.rawMessage)
        assertEquals(Category.GROCERIES, result.category)
        assertEquals(0.85, result.confidence)
        assertEquals(MatchType.FUZZY, result.matchType)
    }

    @Test
    fun `nullable fields are stored as null`() = runTest {
        val transaction = createCategorizedTransaction(
            balance = null,
            cardLast4 = null,
            merchant = null,
            description = null,
            reference = null,
        )

        repository.insertTransaction(transaction)
        val result = repository.getAllTransactions().first()

        assertEquals(null, result.transaction.balance)
        assertEquals(null, result.transaction.cardLast4)
        assertEquals(null, result.transaction.merchant)
        assertEquals(null, result.transaction.description)
        assertEquals(null, result.transaction.reference)
    }

    // =================================
    // Deduplication (INSERT OR REPLACE)
    // =================================

    @Test
    fun `inserting same smsId replaces existing transaction`() = runTest {
        val original = createCategorizedTransaction(smsId = 1, amount = 50000.0)
        val updated = createCategorizedTransaction(smsId = 1, amount = 75000.0)

        repository.insertTransaction(original)
        repository.insertTransaction(updated)

        val result = repository.getAllTransactions()
        assertEquals(1, result.size)
        assertEquals(75000.0, result[0].transaction.amount)
    }

    // =================================
    // getStoredSmsIds
    // =================================

    @Test
    fun `getStoredSmsIds returns all stored SMS IDs`() = runTest {
        repository.insertTransactions(
            listOf(
                createCategorizedTransaction(smsId = 10),
                createCategorizedTransaction(smsId = 20),
                createCategorizedTransaction(smsId = 30),
            ),
        )

        val ids = repository.getStoredSmsIds()
        assertEquals(setOf(10L, 20L, 30L), ids)
    }

    @Test
    fun `getStoredSmsIds returns empty set when no transactions`() = runTest {
        val ids = repository.getStoredSmsIds()
        assertTrue(ids.isEmpty())
    }

    // =================================
    // Date Range Filtering
    // =================================

    @Test
    fun `getTransactionsByDateRange filters correctly`() = runTest {
        val jan = Instant.parse("2024-01-15T10:00:00Z")
        val feb = Instant.parse("2024-02-15T10:00:00Z")
        val mar = Instant.parse("2024-03-15T10:00:00Z")

        repository.insertTransactions(
            listOf(
                createCategorizedTransaction(smsId = 1, timestamp = jan),
                createCategorizedTransaction(smsId = 2, timestamp = feb),
                createCategorizedTransaction(smsId = 3, timestamp = mar),
            ),
        )

        val rangeStart = Instant.parse("2024-02-01T00:00:00Z")
        val rangeEnd = Instant.parse("2024-02-28T23:59:59Z")
        val result = repository.getTransactionsByDateRange(rangeStart, rangeEnd)

        assertEquals(1, result.size)
        assertEquals(2L, result[0].transaction.smsId)
    }

    @Test
    fun `getTransactionsByDateRange returns empty for no matches`() = runTest {
        repository.insertTransaction(
            createCategorizedTransaction(smsId = 1, timestamp = testTimestamp),
        )

        val rangeStart = Instant.parse("2025-01-01T00:00:00Z")
        val rangeEnd = Instant.parse("2025-12-31T23:59:59Z")
        val result = repository.getTransactionsByDateRange(rangeStart, rangeEnd)

        assertTrue(result.isEmpty())
    }

    // =================================
    // Transaction Count
    // =================================

    @Test
    fun `getTransactionCount returns correct count`() = runTest {
        assertEquals(0L, repository.getTransactionCount())

        repository.insertTransactions(
            listOf(
                createCategorizedTransaction(smsId = 1),
                createCategorizedTransaction(smsId = 2),
            ),
        )

        assertEquals(2L, repository.getTransactionCount())
    }

    // =================================
    // Delete All
    // =================================

    @Test
    fun `deleteAllTransactions clears all data`() = runTest {
        repository.insertTransactions(
            listOf(
                createCategorizedTransaction(smsId = 1),
                createCategorizedTransaction(smsId = 2),
            ),
        )
        assertEquals(2L, repository.getTransactionCount())

        repository.deleteAllTransactions()

        assertEquals(0L, repository.getTransactionCount())
        assertTrue(repository.getAllTransactions().isEmpty())
        assertTrue(repository.getStoredSmsIds().isEmpty())
    }

    // =================================
    // Ordering
    // =================================

    @Test
    fun `getAllTransactions returns results ordered by timestamp descending`() = runTest {
        val oldest = Instant.parse("2024-01-01T00:00:00Z")
        val middle = Instant.parse("2024-06-01T00:00:00Z")
        val newest = Instant.parse("2024-12-01T00:00:00Z")

        // Insert in random order
        repository.insertTransactions(
            listOf(
                createCategorizedTransaction(smsId = 1, timestamp = middle),
                createCategorizedTransaction(smsId = 2, timestamp = oldest),
                createCategorizedTransaction(smsId = 3, timestamp = newest),
            ),
        )

        val result = repository.getAllTransactions()
        assertEquals(3L, result[0].transaction.smsId) // newest first
        assertEquals(1L, result[1].transaction.smsId)
        assertEquals(2L, result[2].transaction.smsId) // oldest last
    }

    // =================================
    // Category Enum Mapping
    // =================================

    @Test
    fun `all Category values survive round-trip through database`() = runTest {
        val categories = Category.entries
        val transactions = categories.mapIndexed { index, category ->
            createCategorizedTransaction(
                smsId = index.toLong() + 1,
                category = category,
            )
        }

        repository.insertTransactions(transactions)
        val result = repository.getAllTransactions()

        val storedCategories = result.map { it.category }.toSet()
        assertEquals(categories.toSet(), storedCategories)
    }

    @Test
    fun `all MatchType values survive round-trip through database`() = runTest {
        val matchTypes = MatchType.entries
        val transactions = matchTypes.mapIndexed { index, matchType ->
            createCategorizedTransaction(
                smsId = index.toLong() + 1,
                matchType = matchType,
            )
        }

        repository.insertTransactions(transactions)
        val result = repository.getAllTransactions()

        val storedMatchTypes = result.map { it.matchType }.toSet()
        assertEquals(matchTypes.toSet(), storedMatchTypes)
    }

    @Test
    fun `all TransactionType values survive round-trip through database`() = runTest {
        val types = TransactionType.entries
        val transactions = types.mapIndexed { index, type ->
            createCategorizedTransaction(
                smsId = index.toLong() + 1,
                type = type,
            )
        }

        repository.insertTransactions(transactions)
        val result = repository.getAllTransactions()

        val storedTypes = result.map { it.transaction.type }.toSet()
        assertEquals(types.toSet(), storedTypes)
    }
}
