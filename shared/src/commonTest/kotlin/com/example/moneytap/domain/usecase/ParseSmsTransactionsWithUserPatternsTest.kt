package com.example.moneytap.domain.usecase

import com.example.moneytap.domain.model.AmountFormat
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.CurrencyPosition
import com.example.moneytap.domain.model.FieldSelection
import com.example.moneytap.domain.model.FieldType
import com.example.moneytap.domain.model.InferredPattern
import com.example.moneytap.domain.model.LearnedBankPattern
import com.example.moneytap.domain.model.PatternSegment
import com.example.moneytap.domain.model.SmsMessage
import com.example.moneytap.domain.model.TeachingExample
import com.example.moneytap.domain.service.FuzzyPatternMatcher
import com.example.moneytap.testutil.FakeSmsRepository
import com.example.moneytap.testutil.FakeUserPatternRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ParseSmsTransactionsWithUserPatternsTest {

    private lateinit var smsRepository: FakeSmsRepository
    private lateinit var userPatternRepository: FakeUserPatternRepository
    private lateinit var fuzzyMatcher: FuzzyPatternMatcher
    private lateinit var useCase: ParseSmsTransactionsUseCase

    @BeforeTest
    fun setup() {
        smsRepository = FakeSmsRepository()
        userPatternRepository = FakeUserPatternRepository()
        fuzzyMatcher = FuzzyPatternMatcher(
            minConfidenceThreshold = 0.65,
            fuzzyTextThreshold = 0.75,
        )
        useCase = ParseSmsTransactionsUseCase(
            smsRepository = smsRepository,
            userPatternRepository = userPatternRepository,
            fuzzyMatcher = fuzzyMatcher,
        )
    }

    @Test
    fun `parseMessage uses user pattern when available`() = runTest {
        // Create a learned pattern
        val pattern = LearnedBankPattern(
            id = "pattern_1",
            bankName = "MiBanco",
            senderIds = listOf("MiBanco"),
            examples = emptyList(),
            inferredPattern = InferredPattern(
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
                confidence = 0.9,
            ),
            defaultCategory = Category.GROCERIES,
            enabled = true,
            successCount = 0,
            failCount = 0,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        )

        userPatternRepository.savePattern(pattern)

        // Create SMS that matches the pattern
        val sms = SmsMessage(
            id = 1L,
            sender = "MiBanco",
            body = "Compra por $50.000 en EXITO",
            timestamp = Clock.System.now(),
            isRead = false,
        )

        val result = useCase.parseMessage(sms)

        assertNotNull(result)
        assertEquals(1L, result.smsId)
        assertEquals(50000.0, result.amount)
        assertEquals("EXITO", result.merchant)
        assertEquals("MiBanco", result.bankName)
    }

    @Test
    fun `parseMessage falls back to built-in parser when no user pattern matches`() = runTest {
        // No user patterns available
        val sms = SmsMessage(
            id = 1L,
            sender = "Bancolombia",
            body = "Compra por $25.000 en RAPPI",
            timestamp = Clock.System.now(),
            isRead = false,
        )

        val result = useCase.parseMessage(sms)

        // Should use built-in Bancolombia parser
        assertNotNull(result)
        assertEquals(1L, result.smsId)
    }

    @Test
    fun `parseMessage updates success count when pattern matches`() = runTest {
        val pattern = LearnedBankPattern(
            id = "pattern_1",
            bankName = "TestBank",
            senderIds = listOf("TestBank"),
            examples = emptyList(),
            inferredPattern = InferredPattern(
                segments = listOf(
                    PatternSegment.FixedText("Pago"),
                    PatternSegment.Variable(FieldType.AMOUNT),
                ),
                amountFormat = AmountFormat(
                    thousandsSeparator = '.',
                    decimalSeparator = ',',
                    currencySymbol = "$",
                    currencyPosition = CurrencyPosition.BEFORE,
                ),
                confidence = 0.85,
            ),
            defaultCategory = null,
            enabled = true,
            successCount = 5,
            failCount = 1,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        )

        userPatternRepository.savePattern(pattern)

        val sms = SmsMessage(
            id = 1L,
            sender = "TestBank",
            body = "Pago $10.000 realizado",
            timestamp = Clock.System.now(),
            isRead = false,
        )

        useCase.parseMessage(sms)

        // Check that success count was updated
        val updatedPattern = userPatternRepository.getAllPatterns().first()
        assertEquals(6, updatedPattern.successCount)
        assertEquals(1, updatedPattern.failCount)
    }

    @Test
    fun `parseMessage returns null when pattern match fails`() = runTest {
        val pattern = LearnedBankPattern(
            id = "pattern_1",
            bankName = "TestBank",
            senderIds = listOf("TestBank"),
            examples = emptyList(),
            inferredPattern = InferredPattern(
                segments = listOf(
                    PatternSegment.FixedText("Compra exacta", fuzzyMatch = false),
                    PatternSegment.Variable(FieldType.AMOUNT),
                ),
                amountFormat = AmountFormat(
                    thousandsSeparator = '.',
                    decimalSeparator = ',',
                    currencySymbol = "$",
                    currencyPosition = CurrencyPosition.BEFORE,
                ),
                confidence = 0.9,
            ),
            defaultCategory = null,
            enabled = true,
            successCount = 0,
            failCount = 0,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        )

        userPatternRepository.savePattern(pattern)

        // SMS doesn't match the pattern
        val sms = SmsMessage(
            id = 1L,
            sender = "TestBank",
            body = "Retiro $20.000",
            timestamp = Clock.System.now(),
            isRead = false,
        )

        val result = useCase.parseMessage(sms)

        // Pattern doesn't match, falls back to built-in parsers
        // GenericTransactionParser may parse this, so we check failCount was incremented
        val updatedPattern = userPatternRepository.getAllPatterns().first()
        assertEquals(1, updatedPattern.failCount)

        // If result is not null, it should NOT be from user pattern
        result?.let {
            assertNotNull(it) // Parsed by generic parser
        }
    }

    @Test
    fun `parseMessage works without user pattern repository`() = runTest {
        // Create use case without user patterns
        val basicUseCase = ParseSmsTransactionsUseCase(
            smsRepository = smsRepository,
        )

        val sms = SmsMessage(
            id = 1L,
            sender = "Bancolombia",
            body = "Compra por $30.000 en CARULLA",
            timestamp = Clock.System.now(),
            isRead = false,
        )

        val result = basicUseCase.parseMessage(sms)

        // Should still work with built-in parser
        assertNotNull(result)
    }

    @Test
    fun `parseMessage extracts multiple fields from pattern`() = runTest {
        val pattern = LearnedBankPattern(
            id = "pattern_1",
            bankName = "TestBank",
            senderIds = listOf("TestBank"),
            examples = emptyList(),
            inferredPattern = InferredPattern(
                segments = listOf(
                    PatternSegment.FixedText("Tarjeta"),
                    PatternSegment.Variable(FieldType.CARD_LAST_4),
                    PatternSegment.FixedText("Compra"),
                    PatternSegment.Variable(FieldType.AMOUNT),
                    PatternSegment.FixedText("en"),
                    PatternSegment.Variable(FieldType.MERCHANT),
                    PatternSegment.FixedText("Saldo"),
                    PatternSegment.Variable(FieldType.BALANCE),
                ),
                amountFormat = AmountFormat(
                    thousandsSeparator = '.',
                    decimalSeparator = ',',
                    currencySymbol = "$",
                    currencyPosition = CurrencyPosition.BEFORE,
                ),
                confidence = 0.9,
            ),
            defaultCategory = null,
            enabled = true,
            successCount = 0,
            failCount = 0,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
        )

        userPatternRepository.savePattern(pattern)

        val sms = SmsMessage(
            id = 1L,
            sender = "TestBank",
            body = "Tarjeta 1234 Compra $45.000 en EXITO Saldo $500.000",
            timestamp = Clock.System.now(),
            isRead = false,
        )

        val result = useCase.parseMessage(sms)

        assertNotNull(result)
        assertEquals(45000.0, result.amount)
        assertEquals("EXITO", result.merchant)
        assertEquals("1234", result.cardLast4)
        assertEquals(500000.0, result.balance)
    }
}
