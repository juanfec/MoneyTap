package com.example.moneytap.domain.model

import kotlinx.datetime.*

/**
 * Represents a year-month combination for filtering transactions.
 */
data class YearMonth(private val value: String) : Comparable<YearMonth> {

    init {
        require(value.matches(Regex("""\d{4}-\d{2}"""))) {
            "YearMonth must be in YYYY-MM format"
        }
    }

    val year: Int get() = value.substring(0, 4).toInt()
    val month: Int get() = value.substring(5, 7).toInt()

    /** Returns the first instant of this month (00:00:00 on day 1) */
    fun startOfMonth(timeZone: TimeZone = TimeZone.currentSystemDefault()): Instant {
        val localDate = LocalDate(year, month, 1)
        return localDate.atStartOfDayIn(timeZone)
    }

    /** Returns the last instant of this month (23:59:59.999 on last day) */
    fun endOfMonth(timeZone: TimeZone = TimeZone.currentSystemDefault()): Instant {
        val nextMonth = LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH)
        return nextMonth.atStartOfDayIn(timeZone).minus(1, DateTimeUnit.MILLISECOND)
    }

    /** Human-readable format: "January 2025" */
    fun displayName(): String {
        val monthName = Month(month).name.lowercase().replaceFirstChar { it.uppercase() }
        return "$monthName $year"
    }

    /** Short format: "Jan 2025" */
    fun shortDisplayName(): String {
        val monthName = Month(month).name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        return "$monthName $year"
    }

    fun previous(): YearMonth {
        return if (month == 1) {
            YearMonth("${year - 1}-12")
        } else {
            YearMonth("$year-${(month - 1).toString().padStart(2, '0')}")
        }
    }

    fun next(): YearMonth {
        return if (month == 12) {
            YearMonth("${year + 1}-01")
        } else {
            YearMonth("$year-${(month + 1).toString().padStart(2, '0')}")
        }
    }

    override fun compareTo(other: YearMonth): Int = value.compareTo(other.value)
    override fun toString(): String = value

    companion object {
        fun current(timeZone: TimeZone = TimeZone.currentSystemDefault()): YearMonth {
            val now = Clock.System.now().toLocalDateTime(timeZone)
            return YearMonth("${now.year}-${now.monthNumber.toString().padStart(2, '0')}")
        }

        fun fromInstant(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): YearMonth {
            val local = instant.toLocalDateTime(timeZone)
            return YearMonth("${local.year}-${local.monthNumber.toString().padStart(2, '0')}")
        }
    }
}
