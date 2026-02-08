# Feature: Monthly Balance and Navigation

## Overview

Add the ability to view spending/income per month on the main screen with navigation to select previous months. Users can swipe or tap arrows to navigate between months and see their balance, transactions, and spending breakdown for each month.

## Current State Analysis

### Existing Infrastructure (Already Available)

| Component | Location | What It Does |
|-----------|----------|--------------|
| `MonthlyTotal` | `domain/model/MonthlyTotal.kt` | Data class with `month` (YYYY-MM), `expenses`, `income` |
| `getMonthlyTotals()` | `Transaction.sq` + `TransactionRepository` | SQL query returning last 12 months of aggregated data |
| `getTransactionsByDateRange()` | `TransactionRepository` | Fetches transactions between two dates |
| `SpendingSummary` | `domain/model/SpendingSummary.kt` | Aggregates spending by category (needs month filtering) |
| `idx_timestamp` | `Transaction.sq` | Database index on timestamp for efficient date queries |

### What Needs to Change

| Area | Change Needed |
|------|---------------|
| **UI State** | Add selected month + available months to `SpendingUiState` |
| **ViewModel** | Add month selection logic + filtered data loading |
| **Use Case** | Add month-filtered variant of `GetSpendingByCategoryUseCase` |
| **UI** | Add month selector header with navigation arrows |
| **Repository** | Already has `getTransactionsByDateRange()` — reuse it |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        MONTHLY VIEW FLOW                                │
│                                                                         │
│  ┌──────────────┐    ┌─────────────────┐    ┌───────────────────────┐   │
│  │ Month Header │───▶│ SpendingViewModel│───▶│ GetSpendingByCategory │   │
│  │ < Jan 2025 > │    │ selectedMonth   │    │ UseCase(month filter) │   │
│  └──────────────┘    └─────────────────┘    └───────────────────────┘   │
│                              │                         │                │
│                              ▼                         ▼                │
│                    ┌─────────────────┐      ┌───────────────────────┐   │
│                    │  SpendingUiState │◀────│  TransactionRepository │   │
│                    │  - selectedMonth │      │  getTransactionsByDate │   │
│                    │  - availableMonths│      └───────────────────────┘   │
│                    │  - monthlyBalance │                                 │
│                    └─────────────────┘                                  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## PHASE 1: Domain Layer Updates

### Task 1.1: Create YearMonth value class

**File:** `shared/src/commonMain/kotlin/com/example/moneytap/domain/model/YearMonth.kt`

```kotlin
import kotlinx.datetime.*

/**
 * Represents a year-month combination for filtering transactions.
 * Uses Kotlin value class for zero overhead.
 */
@JvmInline
value class YearMonth(private val value: String) : Comparable<YearMonth> {

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
        val lastDay = LocalDate(year, month, 1)
            .plus(1, DateTimeUnit.MONTH)
            .minus(1, DateTimeUnit.DAY)
        return lastDay.atStartOfDayIn(timeZone)
            .plus(23.hours + 59.minutes + 59.seconds + 999.milliseconds)
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
```

### Task 1.2: Create MonthlySpendingSummary model

**File:** `shared/src/commonMain/kotlin/com/example/moneytap/domain/model/MonthlySpendingSummary.kt`

```kotlin
/**
 * Monthly spending summary with balance information.
 *
 * @property month The year-month this summary represents
 * @property totalIncome Total credits/income for the month
 * @property totalExpenses Total debits/withdrawals for the month
 * @property balance Net balance (income - expenses)
 * @property byCategory Category breakdown (reuses existing SpendingSummary structure)
 * @property transactionCount Total transactions in the month
 */
data class MonthlySpendingSummary(
    val month: YearMonth,
    val totalIncome: Double,
    val totalExpenses: Double,
    val balance: Double = totalIncome - totalExpenses,
    val byCategory: Map<Category, CategorySpending>,
    val transactionCount: Int,
)
```

---

## PHASE 2: Use Case Layer

### Task 2.1: Create GetMonthlySpendingUseCase

**File:** `shared/src/commonMain/kotlin/com/example/moneytap/domain/usecase/GetMonthlySpendingUseCase.kt`

```kotlin
/**
 * Use case for getting spending summary for a specific month.
 *
 * Unlike GetSpendingByCategoryUseCase which processes all transactions,
 * this filters by the selected month for faster queries on historical data.
 */
class GetMonthlySpendingUseCase(
    private val transactionRepository: TransactionRepository,
) {
    /**
     * Returns spending summary for a specific month.
     */
    suspend operator fun invoke(month: YearMonth): Result<MonthlySpendingSummary> {
        return runCatching {
            val transactions = transactionRepository.getTransactionsByDateRange(
                startDate = month.startOfMonth(),
                endDate = month.endOfMonth(),
            )
            aggregateMonthlySpending(month, transactions)
        }
    }

    private fun aggregateMonthlySpending(
        month: YearMonth,
        transactions: List<CategorizedTransaction>,
    ): MonthlySpendingSummary {
        val totalIncome = transactions
            .filter { it.transaction.type == TransactionType.CREDIT }
            .sumOf { it.transaction.amount }

        val totalExpenses = transactions
            .filter { it.transaction.type in listOf(TransactionType.DEBIT, TransactionType.WITHDRAWAL) }
            .sumOf { it.transaction.amount }

        val byCategory = transactions
            .groupBy { it.category }
            .mapValues { (category, txs) ->
                CategorySpending(
                    category = category,
                    totalAmount = txs.sumOf { it.transaction.amount },
                    transactionCount = txs.size,
                    transactions = txs.sortedByDescending { it.transaction.timestamp },
                )
            }
            .entries
            .sortedByDescending { it.value.totalAmount }
            .associate { it.key to it.value }

        return MonthlySpendingSummary(
            month = month,
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            byCategory = byCategory,
            transactionCount = transactions.size,
        )
    }
}
```

### Task 2.2: Create GetAvailableMonthsUseCase

**File:** `shared/src/commonMain/kotlin/com/example/moneytap/domain/usecase/GetAvailableMonthsUseCase.kt`

```kotlin
/**
 * Returns the list of months that have transaction data.
 * Used to populate the month selector and determine navigation bounds.
 */
class GetAvailableMonthsUseCase(
    private val transactionRepository: TransactionRepository,
) {
    /**
     * Returns available months sorted newest first.
     */
    suspend operator fun invoke(): Result<List<YearMonth>> {
        return runCatching {
            transactionRepository.getMonthlyTotals()
                .map { YearMonth(it.month) }
                .sortedDescending()
        }
    }
}
```

---

## PHASE 3: Presentation Layer

### Task 3.1: Update SpendingUiState

**File:** `composeApp/src/commonMain/kotlin/com/example/moneytap/presentation/state/SpendingUiState.kt`

Add new fields to existing data class:

```kotlin
data class SpendingUiState(
    // Existing fields (keep all)
    val isLoading: Boolean = false,
    val isPlatformSupported: Boolean = true,
    val permissionState: PermissionState = PermissionState.Unknown,
    val summary: SpendingSummary? = null,
    val error: SpendingError? = null,

    // NEW: Monthly navigation state
    val selectedMonth: YearMonth? = null,
    val availableMonths: List<YearMonth> = emptyList(),
    val monthlySummary: MonthlySpendingSummary? = null,
    val isMonthlyView: Boolean = true, // Toggle between monthly and all-time view
)
```

### Task 3.2: Update SpendingViewModel

**File:** `composeApp/src/commonMain/kotlin/com/example/moneytap/presentation/viewmodel/SpendingViewModel.kt`

Add month navigation methods:

```kotlin
class SpendingViewModel(
    private val getSpendingByCategoryUseCase: GetSpendingByCategoryUseCase,
    private val getMonthlySpendingUseCase: GetMonthlySpendingUseCase,  // NEW
    private val getAvailableMonthsUseCase: GetAvailableMonthsUseCase,  // NEW
    private val permissionHandler: PermissionHandler,
    private val smsRepository: SmsRepository,
) : ViewModel() {

    // ... existing code ...

    /**
     * Load available months and set initial selection to current month.
     */
    private fun loadAvailableMonths() {
        viewModelScope.launch {
            getAvailableMonthsUseCase()
                .onSuccess { months ->
                    val currentMonth = YearMonth.current()
                    val selectedMonth = if (months.contains(currentMonth)) {
                        currentMonth
                    } else {
                        months.firstOrNull()
                    }

                    _uiState.update {
                        it.copy(
                            availableMonths = months,
                            selectedMonth = selectedMonth,
                        )
                    }

                    selectedMonth?.let { loadMonthlySpending(it) }
                }
        }
    }

    /**
     * Navigate to previous month (if available).
     */
    fun previousMonth() {
        val current = _uiState.value.selectedMonth ?: return
        val available = _uiState.value.availableMonths
        val currentIndex = available.indexOf(current)

        // Previous month = next in list (since sorted descending)
        if (currentIndex < available.lastIndex) {
            selectMonth(available[currentIndex + 1])
        }
    }

    /**
     * Navigate to next month (if available).
     */
    fun nextMonth() {
        val current = _uiState.value.selectedMonth ?: return
        val available = _uiState.value.availableMonths
        val currentIndex = available.indexOf(current)

        // Next month = previous in list (since sorted descending)
        if (currentIndex > 0) {
            selectMonth(available[currentIndex - 1])
        }
    }

    /**
     * Jump to a specific month.
     */
    fun selectMonth(month: YearMonth) {
        _uiState.update { it.copy(selectedMonth = month) }
        loadMonthlySpending(month)
    }

    private fun loadMonthlySpending(month: YearMonth) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            getMonthlySpendingUseCase(month)
                .onSuccess { summary ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            monthlySummary = summary,
                            error = if (summary.transactionCount == 0) {
                                SpendingError.NoTransactions
                            } else {
                                null
                            },
                        )
                    }
                }
                .onFailure { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = SpendingError.Generic(exception.message ?: "Unknown error"),
                        )
                    }
                }
        }
    }

    /**
     * Check if can navigate to previous month.
     */
    val canGoToPreviousMonth: Boolean
        get() {
            val current = _uiState.value.selectedMonth ?: return false
            val available = _uiState.value.availableMonths
            val currentIndex = available.indexOf(current)
            return currentIndex < available.lastIndex
        }

    /**
     * Check if can navigate to next month.
     */
    val canGoToNextMonth: Boolean
        get() {
            val current = _uiState.value.selectedMonth ?: return false
            val available = _uiState.value.availableMonths
            val currentIndex = available.indexOf(current)
            return currentIndex > 0
        }
}
```

---

## PHASE 4: UI Components

### Task 4.1: Create MonthSelector component

**File:** `composeApp/src/commonMain/kotlin/com/example/moneytap/ui/component/MonthSelector.kt`

```kotlin
@Composable
fun MonthSelector(
    selectedMonth: YearMonth,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onPreviousMonth,
            enabled = canGoBack,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous month",
                tint = if (canGoBack) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                },
            )
        }

        Text(
            text = selectedMonth.displayName(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        IconButton(
            onClick = onNextMonth,
            enabled = canGoForward,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
                tint = if (canGoForward) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                },
            )
        }
    }
}
```

### Task 4.2: Create MonthlyBalanceCard component

**File:** `composeApp/src/commonMain/kotlin/com/example/moneytap/ui/component/MonthlyBalanceCard.kt`

```kotlin
@Composable
fun MonthlyBalanceCard(
    summary: MonthlySpendingSummary,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (summary.balance >= 0) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Balance
            Text(
                text = "Balance",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatCurrency(summary.balance),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = if (summary.balance >= 0) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 32.dp))
            Spacer(modifier = Modifier.height(16.dp))

            // Income and Expenses row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Income",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatCurrency(summary.totalIncome),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Expenses",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatCurrency(summary.totalExpenses),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${summary.transactionCount} transactions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

### Task 4.3: Update SpendingSummaryScreen

**File:** `composeApp/src/commonMain/kotlin/com/example/moneytap/ui/screen/SpendingSummaryScreen.kt`

Update to include month selector at the top:

```kotlin
@Composable
private fun SpendingListContent(
    uiState: SpendingUiState,
    onRefresh: () -> Unit,
    onCategoryClick: (categoryName: String) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monthlySummary = uiState.monthlySummary ?: return

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Month selector header
        item(key = "month_selector") {
            uiState.selectedMonth?.let { month ->
                MonthSelector(
                    selectedMonth = month,
                    canGoBack = uiState.availableMonths.indexOf(month) < uiState.availableMonths.lastIndex,
                    canGoForward = uiState.availableMonths.indexOf(month) > 0,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                )
            }
        }

        // Monthly balance card (replaces TotalSpendingCard)
        item(key = "balance_card") {
            MonthlyBalanceCard(summary = monthlySummary)
        }

        item(key = "category_title") {
            Text(
                text = "By Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        items(
            items = monthlySummary.byCategory.values.toList(),
            key = { it.category.name },
        ) { categorySpending ->
            CategorySpendingCard(
                categorySpending = categorySpending,
                totalSpending = monthlySummary.totalExpenses,
                onClick = { onCategoryClick(categorySpending.category.name) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item(key = "refresh") {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Refresh")
            }
        }
    }
}
```

---

## PHASE 5: DI & Integration

### Task 5.1: Update CategorizationModule

**File:** `composeApp/src/commonMain/kotlin/com/example/moneytap/di/CategorizationModule.kt`

```kotlin
val categorizationModule = module {
    // Existing bindings
    singleOf(::CategorizationEngine)
    factoryOf(::CategorizeTransactionsUseCase)
    factoryOf(::GetSpendingByCategoryUseCase)

    // NEW: Monthly spending use cases
    factoryOf(::GetMonthlySpendingUseCase)
    factoryOf(::GetAvailableMonthsUseCase)

    // Update SpendingViewModel factory to include new dependencies
    viewModel {
        SpendingViewModel(
            getSpendingByCategoryUseCase = get(),
            getMonthlySpendingUseCase = get(),
            getAvailableMonthsUseCase = get(),
            permissionHandler = get(),
            smsRepository = get(),
        )
    }
}
```

---

## PHASE 6: Testing

### Task 6.1: YearMonthTest

**File:** `shared/src/commonTest/kotlin/com/example/moneytap/domain/model/YearMonthTest.kt`

Test cases:
- Parsing valid YYYY-MM format
- Rejecting invalid formats
- `startOfMonth()` returns first day at 00:00:00
- `endOfMonth()` returns last day at 23:59:59.999
- `previous()` correctly handles year boundaries (Jan → Dec of previous year)
- `next()` correctly handles year boundaries (Dec → Jan of next year)
- `displayName()` formats correctly ("January 2025")
- `compareTo()` sorts chronologically

### Task 6.2: GetMonthlySpendingUseCaseTest

**File:** `shared/src/commonTest/kotlin/com/example/moneytap/domain/usecase/GetMonthlySpendingUseCaseTest.kt`

Test cases:
- Returns correct totals for single month
- Correctly separates income vs expenses
- Groups by category correctly
- Returns empty summary for month with no transactions
- Handles mixed transaction types

### Task 6.3: GetAvailableMonthsUseCaseTest

**File:** `shared/src/commonTest/kotlin/com/example/moneytap/domain/usecase/GetAvailableMonthsUseCaseTest.kt`

Test cases:
- Returns months sorted descending (newest first)
- Returns empty list when no transactions
- Deduplicates months correctly

---

## Implementation Order

1. **Phase 1** — Domain models (`YearMonth`, `MonthlySpendingSummary`)
2. **Phase 6.1** — `YearMonthTest` (test-first for value class)
3. **Phase 2** — Use cases (`GetMonthlySpendingUseCase`, `GetAvailableMonthsUseCase`)
4. **Phase 6.2-6.3** — Use case tests
5. **Phase 3** — Update `SpendingUiState` and `SpendingViewModel`
6. **Phase 4** — UI components (`MonthSelector`, `MonthlyBalanceCard`, update screen)
7. **Phase 5** — DI integration

---

## UI Wireframe

```
┌──────────────────────────────────────┐
│          Spending Summary            │  ← TopAppBar (unchanged)
├──────────────────────────────────────┤
│  ◀    January 2025    ▶              │  ← MonthSelector
├──────────────────────────────────────┤
│  ┌──────────────────────────────┐    │
│  │         Balance              │    │
│  │        $500,000 COP          │    │  ← MonthlyBalanceCard
│  │  ─────────────────────────── │    │
│  │   Income        Expenses     │    │
│  │ $2,500,000    $2,000,000     │    │
│  │        45 transactions       │    │
│  └──────────────────────────────┘    │
│                                      │
│  By Category                         │
│  ┌──────────────────────────────┐    │
│  │ 🍕 Restaurant       $450,000 │    │  ← CategorySpendingCard (unchanged)
│  └──────────────────────────────┘    │
│  ┌──────────────────────────────┐    │
│  │ 🛒 Groceries        $380,000 │    │
│  └──────────────────────────────┘    │
│  ...                                 │
│  [        Refresh        ]           │
└──────────────────────────────────────┘
```

---

## Optional Enhancements (Future)

1. **Horizontal pager** — Swipe between months instead of buttons
2. **Mini chart** — Show income/expense trend for last 6 months
3. **Month picker dialog** — Jump to any month directly
4. **Comparison view** — Compare current month vs previous
5. **Export** — Export monthly report as CSV/PDF

---

## Verification

1. **Build:** `./gradlew :composeApp:assembleDebug`
2. **Tests:** `./gradlew :shared:jvmTest`
3. **Manual testing:**
   - Load app with existing transaction data
   - Verify current month is selected by default
   - Navigate to previous months and verify data changes
   - Verify balance calculation (income - expenses)
   - Verify category breakdown matches month filter
   - Verify navigation buttons disable at boundaries
