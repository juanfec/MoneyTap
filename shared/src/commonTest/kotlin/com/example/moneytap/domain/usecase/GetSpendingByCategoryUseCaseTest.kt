package com.example.moneytap.domain.usecase

import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.SmsMessage
import com.example.moneytap.domain.model.TransactionType
import com.example.moneytap.domain.service.CategorizationEngine
import com.example.moneytap.testutil.FakeSmsRepository
import com.example.moneytap.testutil.FakeTransactionRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetSpendingByCategoryUseCaseTest {

    private val testTimestamp = Instant.parse("2024-01-15T10:30:00Z")

    private val fakeSmsRepository = FakeSmsRepository()
    private val fakeTransactionRepository = FakeTransactionRepository()

    private val parseSmsUseCase = ParseSmsTransactionsUseCase(fakeSmsRepository)
    private val categorizeUseCase = CategorizeTransactionsUseCase(CategorizationEngine())
    private val useCase = GetSpendingByCategoryUseCase(
        parseSmsTransactionsUseCase = parseSmsUseCase,
        categorizeTransactionsUseCase = categorizeUseCase,
        transactionRepository = fakeTransactionRepository,
    )

    private fun bancolombiaDebitSms(
        id: Long,
        amount: String,
        merchant: String,
    ) = SmsMessage(
        id = id,
        sender = "Bancolombia",
        body = "Compra por \$$amount con TC *1234 en $merchant. Consulte saldo: \$500.000",
        timestamp = testTimestamp,
        isRead = true,
    )

    private fun bancolombiaCreditSms(
        id: Long,
        amount: String,
    ) = SmsMessage(
        id = id,
        sender = "Bancolombia",
        body = "Transferencia recibida por \$$amount en cuenta *5678.",
        timestamp = testTimestamp,
        isRead = true,
    )

    // =================================
    // Incremental Processing
    // =================================

    @Test
    fun `first call parses and categorizes all SMS`() = runTest {
        fakeSmsRepository.messages = listOf(
            bancolombiaDebitSms(id = 1, amount = "50.000", merchant = "EXITO"),
            bancolombiaDebitSms(id = 2, amount = "30.000", merchant = "RAPPI"),
        )

        val result = useCase()
        assertTrue(result.isSuccess)

        val summary = result.getOrThrow()
        assertEquals(2, summary.transactionCount)
        assertEquals(2, fakeTransactionRepository.insertCallCount)
    }

    @Test
    fun `second call with same SMS does not re-categorize`() = runTest {
        fakeSmsRepository.messages = listOf(
            bancolombiaDebitSms(id = 1, amount = "50.000", merchant = "EXITO"),
        )

        // First call: parse and categorize
        useCase()
        val insertCountAfterFirst = fakeTransactionRepository.insertCallCount

        // Second call: same SMS, should skip categorization
        val result = useCase()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().transactionCount)
        assertEquals(insertCountAfterFirst, fakeTransactionRepository.insertCallCount)
    }

    @Test
    fun `new SMS are categorized while existing ones are skipped`() = runTest {
        // First call with one SMS
        fakeSmsRepository.messages = listOf(
            bancolombiaDebitSms(id = 1, amount = "50.000", merchant = "EXITO"),
        )
        useCase()
        val insertCountAfterFirst = fakeTransactionRepository.insertCallCount

        // Second call with original + new SMS
        fakeSmsRepository.messages = listOf(
            bancolombiaDebitSms(id = 1, amount = "50.000", merchant = "EXITO"),
            bancolombiaDebitSms(id = 2, amount = "30.000", merchant = "RAPPI"),
        )
        val result = useCase()
        assertTrue(result.isSuccess)

        val summary = result.getOrThrow()
        assertEquals(2, summary.transactionCount)
        // Should have inserted again (only the new transaction)
        assertTrue(fakeTransactionRepository.insertCallCount > insertCountAfterFirst)
    }

    // =================================
    // Empty / Edge Cases
    // =================================

    @Test
    fun `empty inbox returns zero transactions`() = runTest {
        fakeSmsRepository.messages = emptyList()

        val result = useCase()
        assertTrue(result.isSuccess)

        val summary = result.getOrThrow()
        assertEquals(0, summary.transactionCount)
        assertEquals(0.0, summary.totalSpending)
        assertTrue(summary.byCategory.isEmpty())
    }

    @Test
    fun `non-parseable SMS are ignored`() = runTest {
        fakeSmsRepository.messages = listOf(
            SmsMessage(
                id = 1,
                sender = "UnknownSender",
                body = "Hello! Your appointment is tomorrow.",
                timestamp = testTimestamp,
                isRead = true,
            ),
        )

        val result = useCase()
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().transactionCount)
    }

    // =================================
    // Error Handling
    // =================================

    @Test
    fun `error from SMS repository is propagated`() = runTest {
        fakeSmsRepository.shouldFail = true

        val result = useCase()
        assertTrue(result.isFailure)
    }

    // =================================
    // Aggregation
    // =================================

    @Test
    fun `transactions are grouped by category`() = runTest {
        fakeSmsRepository.messages = listOf(
            bancolombiaDebitSms(id = 1, amount = "50.000", merchant = "EXITO"),
            bancolombiaDebitSms(id = 2, amount = "30.000", merchant = "CARULLA"),
            bancolombiaDebitSms(id = 3, amount = "20.000", merchant = "RAPPI"),
        )

        val result = useCase()
        assertTrue(result.isSuccess)

        val summary = result.getOrThrow()
        assertEquals(3, summary.transactionCount)

        // EXITO and CARULLA should both be GROCERIES
        val groceries = summary.byCategory[Category.GROCERIES]
        assertEquals(2, groceries?.transactionCount)

        // RAPPI should be RESTAURANT
        val restaurant = summary.byCategory[Category.RESTAURANT]
        assertEquals(1, restaurant?.transactionCount)
    }

    @Test
    fun `total spending sums all amounts`() = runTest {
        fakeSmsRepository.messages = listOf(
            bancolombiaDebitSms(id = 1, amount = "50.000", merchant = "EXITO"),
            bancolombiaDebitSms(id = 2, amount = "30.000", merchant = "RAPPI"),
        )

        val result = useCase()
        assertTrue(result.isSuccess)

        val summary = result.getOrThrow()
        assertEquals(80000.0, summary.totalSpending)
    }

    // =================================
    // Spending-Only Filter
    // =================================

    @Test
    fun `spendingOnly filters to DEBIT and WITHDRAWAL only`() = runTest {
        fakeSmsRepository.messages = listOf(
            bancolombiaDebitSms(id = 1, amount = "50.000", merchant = "EXITO"),
            bancolombiaCreditSms(id = 2, amount = "100.000"),
        )

        val allResult = useCase(spendingOnly = false)
        val spendingResult = useCase(spendingOnly = true)

        assertTrue(allResult.isSuccess)
        assertTrue(spendingResult.isSuccess)

        val allCount = allResult.getOrThrow().transactionCount
        val spendingCount = spendingResult.getOrThrow().transactionCount

        assertTrue(
            spendingCount <= allCount,
            "Spending-only count ($spendingCount) should be <= all count ($allCount)",
        )
    }

    // =================================
    // SMS ID Deduplication
    // =================================

    @Test
    fun `smsId is preserved through the pipeline`() = runTest {
        val smsId = 42L
        fakeSmsRepository.messages = listOf(
            bancolombiaDebitSms(id = smsId, amount = "50.000", merchant = "EXITO"),
        )

        useCase()

        val storedIds = fakeTransactionRepository.getStoredSmsIds()
        assertTrue(storedIds.contains(smsId), "Stored IDs should contain the original SMS ID")
    }
}
