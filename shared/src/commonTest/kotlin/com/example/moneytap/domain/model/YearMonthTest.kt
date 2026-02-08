package com.example.moneytap.domain.model

import kotlinx.datetime.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class YearMonthTest {

    @Test
    fun `should parse valid YYYY-MM format`() {
        val yearMonth = YearMonth("2025-01")
        assertEquals(2025, yearMonth.year)
        assertEquals(1, yearMonth.month)
        assertEquals("2025-01", yearMonth.toString())
    }

    @Test
    fun `should reject invalid format`() {
        assertFailsWith<IllegalArgumentException> {
            YearMonth("2025-1")
        }
        assertFailsWith<IllegalArgumentException> {
            YearMonth("25-01")
        }
        assertFailsWith<IllegalArgumentException> {
            YearMonth("2025/01")
        }
        assertFailsWith<IllegalArgumentException> {
            YearMonth("202501")
        }
    }

    @Test
    fun `startOfMonth should return first day at midnight`() {
        val yearMonth = YearMonth("2025-01")
        val start = yearMonth.startOfMonth(TimeZone.UTC)

        val localDateTime = start.toLocalDateTime(TimeZone.UTC)
        assertEquals(2025, localDateTime.year)
        assertEquals(1, localDateTime.monthNumber)
        assertEquals(1, localDateTime.dayOfMonth)
        assertEquals(0, localDateTime.hour)
        assertEquals(0, localDateTime.minute)
        assertEquals(0, localDateTime.second)
    }

    @Test
    fun `endOfMonth should return last day at end of day`() {
        val january = YearMonth("2025-01")
        val endJan = january.endOfMonth(TimeZone.UTC)
        val localEndJan = endJan.toLocalDateTime(TimeZone.UTC)
        assertEquals(31, localEndJan.dayOfMonth)
        assertEquals(23, localEndJan.hour)
        assertEquals(59, localEndJan.minute)

        val february = YearMonth("2025-02")
        val endFeb = february.endOfMonth(TimeZone.UTC)
        val localEndFeb = endFeb.toLocalDateTime(TimeZone.UTC)
        assertEquals(28, localEndFeb.dayOfMonth) // 2025 is not a leap year
    }

    @Test
    fun `previous should handle year boundaries`() {
        val january = YearMonth("2025-01")
        val december = january.previous()
        assertEquals("2024-12", december.toString())

        val march = YearMonth("2025-03")
        val february = march.previous()
        assertEquals("2025-02", february.toString())
    }

    @Test
    fun `next should handle year boundaries`() {
        val december = YearMonth("2024-12")
        val january = december.next()
        assertEquals("2025-01", january.toString())

        val february = YearMonth("2025-02")
        val march = february.next()
        assertEquals("2025-03", march.toString())
    }

    @Test
    fun `displayName should format correctly`() {
        assertEquals("January 2025", YearMonth("2025-01").displayName())
        assertEquals("December 2024", YearMonth("2024-12").displayName())
        assertEquals("February 2025", YearMonth("2025-02").displayName())
    }

    @Test
    fun `shortDisplayName should format correctly`() {
        assertEquals("Jan 2025", YearMonth("2025-01").shortDisplayName())
        assertEquals("Dec 2024", YearMonth("2024-12").shortDisplayName())
        assertEquals("Feb 2025", YearMonth("2025-02").shortDisplayName())
    }

    @Test
    fun `compareTo should sort chronologically`() {
        val jan2024 = YearMonth("2024-01")
        val dec2024 = YearMonth("2024-12")
        val jan2025 = YearMonth("2025-01")

        assertTrue(jan2024 < dec2024)
        assertTrue(dec2024 < jan2025)
        assertTrue(jan2024 < jan2025)

        val sorted = listOf(jan2025, jan2024, dec2024).sorted()
        assertEquals(listOf(jan2024, dec2024, jan2025), sorted)
    }

    @Test
    fun `current should return current month`() {
        val now = Clock.System.now()
        val localNow = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val current = YearMonth.current()

        assertEquals(localNow.year, current.year)
        assertEquals(localNow.monthNumber, current.month)
    }

    @Test
    fun `fromInstant should extract month from instant`() {
        val instant = LocalDate(2025, 6, 15)
            .atStartOfDayIn(TimeZone.UTC)
        val yearMonth = YearMonth.fromInstant(instant, TimeZone.UTC)

        assertEquals("2025-06", yearMonth.toString())
    }
}
