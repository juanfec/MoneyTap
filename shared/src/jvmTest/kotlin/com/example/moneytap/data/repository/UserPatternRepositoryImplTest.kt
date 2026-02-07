package com.example.moneytap.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.moneytap.data.database.MoneyTapDatabase
import com.example.moneytap.domain.model.AmountFormat
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.CurrencyPosition
import com.example.moneytap.domain.model.FieldSelection
import com.example.moneytap.domain.model.FieldType
import com.example.moneytap.domain.model.InferredPattern
import com.example.moneytap.domain.model.LearnedBankPattern
import com.example.moneytap.domain.model.PatternSegment
import com.example.moneytap.domain.model.TeachingExample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserPatternRepositoryImplTest {

    private lateinit var database: MoneyTapDatabase
    private lateinit var repository: UserPatternRepositoryImpl

    @BeforeTest
    fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MoneyTapDatabase.Schema.create(driver)
        // Enable foreign keys for CASCADE DELETE to work
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        database = MoneyTapDatabase(driver)
        repository = UserPatternRepositoryImpl(database, Dispatchers.Unconfined)
    }

    private fun createTestPattern(
        id: String = "pattern_1",
        bankName: String = "TestBank",
        senderIds: List<String> = listOf("TestBank", "TestBank123"),
        defaultCategory: Category? = Category.GROCERIES,
        enabled: Boolean = true,
    ): LearnedBankPattern {
        val example = TeachingExample(
            id = "example_1",
            smsBody = "Compra por $50.000 en EXITO",
            senderId = "TestBank",
            selections = listOf(
                FieldSelection(FieldType.AMOUNT, 11, 19, "$50.000"),
                FieldSelection(FieldType.MERCHANT, 23, 28, "EXITO"),
            ),
            category = null,
            createdAt = Clock.System.now(),
        )

        val pattern = InferredPattern(
            segments = listOf(
                PatternSegment.FixedText("Compra por"),
                PatternSegment.Variable(FieldType.AMOUNT),
                PatternSegment.FixedText("en"),
                PatternSegment.Variable(FieldType.MERCHANT),
            ),
            amountFormat = AmountFormat(
                thousandsSeparator = '.',
                decimalSeparator = ',',
                currencySymbol = "$",
                currencyPosition = CurrencyPosition.BEFORE,
            ),
            confidence = 0.85,
        )

        return LearnedBankPattern(
            id = id,
            bankName = bankName,
            senderIds = senderIds,
            examples = listOf(example),
            inferredPattern = pattern,
            defaultCategory = defaultCategory,
            enabled = enabled,
            successCount = 0,
            failCount = 0,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        )
    }

    @Test
    fun `savePattern stores and retrieves a pattern`() = runTest {
        val pattern = createTestPattern()

        repository.savePattern(pattern)
        val result = repository.getAllPatterns()

        assertEquals(1, result.size)
        assertEquals(pattern.id, result[0].id)
        assertEquals(pattern.bankName, result[0].bankName)
        assertEquals(pattern.senderIds, result[0].senderIds)
        assertEquals(pattern.defaultCategory, result[0].defaultCategory)
    }

    @Test
    fun `savePattern replaces existing pattern with same id`() = runTest {
        val pattern1 = createTestPattern(id = "pattern_1", bankName = "BankA")
        val pattern2 = createTestPattern(id = "pattern_1", bankName = "BankB")

        repository.savePattern(pattern1)
        repository.savePattern(pattern2)
        val result = repository.getAllPatterns()

        assertEquals(1, result.size)
        assertEquals("BankB", result[0].bankName)
    }

    @Test
    fun `getAllPatterns returns only enabled patterns`() = runTest {
        val enabledPattern = createTestPattern(id = "pattern_1", enabled = true)
        val disabledPattern = createTestPattern(id = "pattern_2", enabled = false)

        repository.savePattern(enabledPattern)
        repository.savePattern(disabledPattern)
        val result = repository.getAllPatterns()

        assertEquals(1, result.size)
        assertEquals("pattern_1", result[0].id)
    }

    @Test
    fun `getPatternBySenderId finds pattern by sender id`() = runTest {
        val pattern = createTestPattern(
            senderIds = listOf("TestBank", "TestBank123"),
        )

        repository.savePattern(pattern)

        val result1 = repository.getPatternBySenderId("TestBank")
        val result2 = repository.getPatternBySenderId("TestBank123")
        val result3 = repository.getPatternBySenderId("OtherBank")

        assertNotNull(result1)
        assertEquals(pattern.id, result1.id)
        assertNotNull(result2)
        assertEquals(pattern.id, result2.id)
        assertNull(result3)
    }

    @Test
    fun `getPatternBySenderId is case insensitive`() = runTest {
        val pattern = createTestPattern(
            senderIds = listOf("TestBank"),
        )

        repository.savePattern(pattern)

        val result1 = repository.getPatternBySenderId("testbank")
        val result2 = repository.getPatternBySenderId("TESTBANK")
        val result3 = repository.getPatternBySenderId("TeStBaNk")

        assertNotNull(result1)
        assertNotNull(result2)
        assertNotNull(result3)
    }

    @Test
    fun `updatePatternStats updates success and fail counts`() = runTest {
        val pattern = createTestPattern(id = "pattern_1")

        repository.savePattern(pattern)
        repository.updatePatternStats("pattern_1", successCount = 5, failCount = 2)

        val result = repository.getAllPatterns().first()

        assertEquals(5, result.successCount)
        assertEquals(2, result.failCount)
    }

    @Test
    fun `deletePattern removes pattern and associated examples`() = runTest {
        val pattern = createTestPattern(id = "pattern_1")

        repository.savePattern(pattern)

        val example = TeachingExample(
            id = "example_2",
            smsBody = "Test SMS",
            senderId = "TestBank",
            selections = emptyList(),
            category = null,
            createdAt = Clock.System.now(),
        )
        repository.saveTeachingExample(example, "pattern_1")

        repository.deletePattern("pattern_1")

        val patterns = repository.getAllPatterns()
        val examples = repository.getExamplesForPattern("pattern_1")

        assertEquals(0, patterns.size)
        assertEquals(0, examples.size)
    }

    @Test
    fun `saveTeachingExample stores example for pattern`() = runTest {
        val pattern = createTestPattern(id = "pattern_1")
        repository.savePattern(pattern)

        val example = TeachingExample(
            id = "example_2",
            smsBody = "Test SMS message",
            senderId = "TestBank",
            selections = listOf(
                FieldSelection(FieldType.AMOUNT, 0, 5, "50000"),
            ),
            category = Category.RESTAURANT,
            createdAt = Clock.System.now(),
        )

        repository.saveTeachingExample(example, "pattern_1")
        val result = repository.getExamplesForPattern("pattern_1")

        assertEquals(1, result.size)
        assertEquals(example.id, result[0].id)
        assertEquals(example.smsBody, result[0].smsBody)
        assertEquals(example.senderId, result[0].senderId)
        assertEquals(example.category, result[0].category)
    }

    @Test
    fun `getExamplesForPattern returns empty list for non-existent pattern`() = runTest {
        val result = repository.getExamplesForPattern("non_existent")

        assertEquals(0, result.size)
    }

    @Test
    fun `pattern preserves inferred pattern structure`() = runTest {
        val pattern = createTestPattern()

        repository.savePattern(pattern)
        val result = repository.getAllPatterns().first()

        assertEquals(4, result.inferredPattern.segments.size)
        assertEquals(0.85, result.inferredPattern.confidence)
        assertEquals('.', result.inferredPattern.amountFormat.thousandsSeparator)
        assertEquals(',', result.inferredPattern.amountFormat.decimalSeparator)
        assertEquals("$", result.inferredPattern.amountFormat.currencySymbol)
        assertEquals(CurrencyPosition.BEFORE, result.inferredPattern.amountFormat.currencyPosition)
    }

    @Test
    fun `pattern with null default category is handled correctly`() = runTest {
        val pattern = createTestPattern(defaultCategory = null)

        repository.savePattern(pattern)
        val result = repository.getAllPatterns().first()

        assertNull(result.defaultCategory)
    }

    @Test
    fun `multiple patterns can be stored and retrieved`() = runTest {
        val pattern1 = createTestPattern(id = "pattern_1", bankName = "BankA")
        val pattern2 = createTestPattern(id = "pattern_2", bankName = "BankB")
        val pattern3 = createTestPattern(id = "pattern_3", bankName = "BankC")

        repository.savePattern(pattern1)
        repository.savePattern(pattern2)
        repository.savePattern(pattern3)

        val result = repository.getAllPatterns()

        assertEquals(3, result.size)
    }
}
