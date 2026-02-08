package com.example.moneytap.domain.usecase

import com.example.moneytap.domain.model.MonthlyTotal
import com.example.moneytap.domain.model.YearMonth
import com.example.moneytap.testutil.FakeTransactionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetAvailableMonthsUseCaseTest {

    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var useCase: GetAvailableMonthsUseCase

    @BeforeTest
    fun setup() {
        transactionRepository = FakeTransactionRepository()
        useCase = GetAvailableMonthsUseCase(transactionRepository)
    }

    @Test
    fun `should return months sorted descending`() = runTest {
        val monthlyTotals = listOf(
            MonthlyTotal("2024-12", expenses = 100.0, income = 200.0),
            MonthlyTotal("2025-01", expenses = 150.0, income = 250.0),
            MonthlyTotal("2025-02", expenses = 120.0, income = 220.0),
        )

        transactionRepository.setMonthlyTotals(monthlyTotals)

        val result = useCase().getOrThrow()

        assertEquals(3, result.size)
        assertEquals(YearMonth("2025-02"), result[0])
        assertEquals(YearMonth("2025-01"), result[1])
        assertEquals(YearMonth("2024-12"), result[2])
    }

    @Test
    fun `should return empty list when no transactions`() = runTest {
        transactionRepository.setMonthlyTotals(emptyList())

        val result = useCase().getOrThrow()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `should handle single month`() = runTest {
        val monthlyTotals = listOf(
            MonthlyTotal("2025-01", expenses = 100.0, income = 200.0),
        )

        transactionRepository.setMonthlyTotals(monthlyTotals)

        val result = useCase().getOrThrow()

        assertEquals(1, result.size)
        assertEquals(YearMonth("2025-01"), result[0])
    }
}
