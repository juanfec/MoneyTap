# Feature: Manual Transaction Categorization

## Overview

Allow users to manually change the category of any transaction. User-corrected categories should persist and take priority over automatic categorization on future app launches.

## Current State Analysis

### Existing Infrastructure

| Component | Location | Status |
|-----------|----------|--------|
| `Category` enum | `domain/model/Category.kt` | ✅ 12 categories available |
| `userCorrected` flag | `Transaction.sq` | ✅ Exists but unused |
| `TransactionDetailScreen` | `ui/screen/TransactionDetailScreen.kt` | ✅ Shows transaction details |
| `TransactionRepository` | `domain/repository/TransactionRepository.kt` | ⚠️ Missing update method |
| `CategorizedTransaction` | `domain/model/CategorizedTransaction.kt` | ⚠️ Missing `userCorrected` field |

### What Needs to Change

| Area | Change |
|------|--------|
| **Database** | Add `updateCategory` query to Transaction.sq |
| **Domain Model** | Add `userCorrected: Boolean` to `CategorizedTransaction` |
| **Repository** | Add `updateTransactionCategory()` method |
| **Use Case** | Create `UpdateTransactionCategoryUseCase` |
| **ViewModel** | Add `changeCategory()` method to SpendingViewModel |
| **UI** | Add category picker dialog/bottom sheet |
| **Detail Screen** | Add "Change Category" button |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     MANUAL CATEGORIZATION FLOW                              │
│                                                                             │
│  ┌──────────────────┐    ┌─────────────────┐    ┌───────────────────────┐   │
│  │TransactionDetail │───▶│ CategoryPicker  │───▶│ SpendingViewModel    │   │
│  │    Screen        │    │  BottomSheet    │    │ changeCategory()     │   │
│  └──────────────────┘    └─────────────────┘    └───────────────────────┘   │
│                                                           │                 │
│                                                           ▼                 │
│                                               ┌───────────────────────┐     │
│                                               │ UpdateTransaction     │     │
│                                               │ CategoryUseCase       │     │
│                                               └───────────────────────┘     │
│                                                           │                 │
│                                                           ▼                 │
│                                               ┌───────────────────────┐     │
│                                               │ TransactionRepository │     │
│                                               │ updateCategory()      │     │
│                                               └───────────────────────┘     │
│                                                           │                 │
│                                                           ▼                 │
│                                               ┌───────────────────────┐     │
│                                               │ SQLDelight Database   │     │
│                                               │ userCorrected = 1     │     │
│                                               └───────────────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## PHASE 1: Database Layer

### Task 1.1: Add updateCategory query

**File:** `shared/src/commonMain/sqldelight/com/example/moneytap/data/database/Transaction.sq`

Add new query:

```sql
updateCategory:
UPDATE TransactionEntity
SET category = ?, matchType = 'USER_RULE', confidence = 1.0, userCorrected = 1
WHERE smsId = ?;
```

### Task 1.2: Add getTransactionBySmsId to repository interface

**File:** `shared/src/commonMain/kotlin/com/example/moneytap/domain/repository/TransactionRepository.kt`

Add method:

```kotlin
/**
 * Returns a single transaction by SMS ID, or null if not found.
 */
suspend fun getTransactionBySmsId(smsId: Long): CategorizedTransaction?

/**
 * Updates the category of a transaction and marks it as user-corrected.
 */
suspend fun updateTransactionCategory(smsId: Long, newCategory: Category)
```

---

## PHASE 2: Domain Model Update

### Task 2.1: Add userCorrected field to CategorizedTransaction

**File:** `shared/src/commonMain/kotlin/com/example/moneytap/domain/model/CategorizedTransaction.kt`

```kotlin
data class CategorizedTransaction(
    val transaction: TransactionInfo,
    val category: Category,
    val confidence: Double,
    val matchType: MatchType,
    val userCorrected: Boolean = false,  // NEW
)
```

### Task 2.2: Update TransactionRepositoryImpl

**File:** `shared/src/commonMain/kotlin/com/example/moneytap/data/repository/TransactionRepositoryImpl.kt`

1. Update `mapEntityToDomain()` to include `userCorrected`
2. Implement `getTransactionBySmsId()`
3. Implement `updateTransactionCategory()`

```kotlin
override suspend fun getTransactionBySmsId(smsId: Long): CategorizedTransaction? =
    withContext(Dispatchers.Default) {
        queries.getTransactionBySmsId(smsId).executeAsOneOrNull()?.let { entity ->
            mapEntityToDomain(entity)
        }
    }

override suspend fun updateTransactionCategory(smsId: Long, newCategory: Category) {
    withContext(Dispatchers.Default) {
        queries.updateCategory(
            category = newCategory.name,
            smsId = smsId,
        )
    }
}
```

---

## PHASE 3: Use Case Layer

### Task 3.1: Create UpdateTransactionCategoryUseCase

**File:** `shared/src/commonMain/kotlin/com/example/moneytap/domain/usecase/UpdateTransactionCategoryUseCase.kt`

```kotlin
/**
 * Updates the category of a transaction and marks it as user-corrected.
 */
class UpdateTransactionCategoryUseCase(
    private val transactionRepository: TransactionRepository,
) {
    suspend operator fun invoke(smsId: Long, newCategory: Category): Result<Unit> {
        return runCatching {
            transactionRepository.updateTransactionCategory(smsId, newCategory)
        }
    }
}
```

---

## PHASE 4: Presentation Layer

### Task 4.1: Update SpendingViewModel

**File:** `composeApp/src/commonMain/kotlin/com/example/moneytap/presentation/viewmodel/SpendingViewModel.kt`

Add method:

```kotlin
private val updateTransactionCategoryUseCase: UpdateTransactionCategoryUseCase

fun changeTransactionCategory(smsId: Long, newCategory: Category) {
    viewModelScope.launch {
        updateTransactionCategoryUseCase(smsId, newCategory)
            .onSuccess {
                // Reload current month to reflect the change
                _uiState.value.selectedMonth?.let { loadMonthlySpending(it) }
            }
            .onFailure { exception ->
                _uiState.update {
                    it.copy(error = SpendingError.Generic(exception.message ?: "Failed to update category"))
                }
            }
    }
}
```

### Task 4.2: Create CategoryPickerBottomSheet

**File:** `composeApp/src/commonMain/kotlin/com/example/moneytap/ui/component/CategoryPickerBottomSheet.kt`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerBottomSheet(
    currentCategory: Category,
    onCategorySelected: (Category) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "Select Category",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Group by PrimaryCategory
            PrimaryCategory.entries.forEach { primaryCategory ->
                Text(
                    text = primaryCategory.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )

                Category.entries
                    .filter { it.primaryCategory == primaryCategory }
                    .forEach { category ->
                        CategoryItem(
                            category = category,
                            isSelected = category == currentCategory,
                            onClick = {
                                onCategorySelected(category)
                                onDismiss()
                            },
                        )
                    }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CategoryItem(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = category.displayName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
```

---

## PHASE 5: UI Integration

### Task 5.1: Update TransactionDetailScreen

**File:** `composeApp/src/commonMain/kotlin/com/example/moneytap/ui/screen/TransactionDetailScreen.kt`

Add category change button and bottom sheet:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    categoryName: String,
    transactionIndex: Int,
    viewModel: SpendingViewModel,
    onNavigateBack: () -> Unit,
    onTeachPattern: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCategoryPicker by remember { mutableStateOf(false) }

    // ... existing code ...

    // In TransactionDetailContent, add:
    item(key = "change_category_button") {
        OutlinedButton(
            onClick = { showCategoryPicker = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text("Change Category")
        }
    }

    // Bottom sheet
    if (showCategoryPicker && transaction != null) {
        CategoryPickerBottomSheet(
            currentCategory = transaction.category,
            onCategorySelected = { newCategory ->
                viewModel.changeTransactionCategory(
                    smsId = transaction.transaction.smsId,
                    newCategory = newCategory,
                )
            },
            onDismiss = { showCategoryPicker = false },
        )
    }
}
```

### Task 5.2: Show "User Corrected" badge

In `TransactionDetailScreen`, show a badge when `userCorrected = true`:

```kotlin
if (transaction.userCorrected) {
    AssistChip(
        onClick = {},
        label = { Text("Manually categorized") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
    )
}
```

---

## PHASE 6: DI Configuration

### Task 6.1: Update CategorizationModule

**File:** `composeApp/src/commonMain/kotlin/com/example/moneytap/di/CategorizationModule.kt`

```kotlin
factoryOf(::UpdateTransactionCategoryUseCase)
```

Update SpendingViewModel factory to include the new use case.

---

## PHASE 7: Testing

### Task 7.1: UpdateTransactionCategoryUseCaseTest

**File:** `shared/src/commonTest/kotlin/com/example/moneytap/domain/usecase/UpdateTransactionCategoryUseCaseTest.kt`

Test cases:
- Successfully updates category
- Returns error when transaction not found
- Sets matchType to USER_RULE
- Sets confidence to 1.0
- Sets userCorrected to true

### Task 7.2: Update FakeTransactionRepository

Add mock implementations for:
- `getTransactionBySmsId()`
- `updateTransactionCategory()`

### Task 7.3: TransactionRepositoryImplTest

Test the actual database update:
- Insert transaction → update category → verify change persisted
- Verify userCorrected flag is set

---

## Implementation Order

1. **Phase 1** — Database (add query)
2. **Phase 2** — Domain model + repository implementation
3. **Phase 3** — Use case
4. **Phase 7.1-7.2** — Tests for use case
5. **Phase 4** — ViewModel update
6. **Phase 5** — UI components (bottom sheet, button integration)
7. **Phase 6** — DI configuration
8. **Phase 7.3** — Repository integration test

---

## UI Wireframe

### Transaction Detail Screen (with change button)

```
┌──────────────────────────────────────┐
│  ←     Transaction Details           │
├──────────────────────────────────────┤
│  ┌──────────────────────────────┐    │
│  │         Purchase             │    │
│  │       $150,000 COP           │    │
│  └──────────────────────────────┘    │
│                                      │
│  Details                             │
│  ─────────────────────────────────   │
│  Merchant          EXITO AMERICAS    │
│  Bank              Bancolombia       │
│  Category          Groceries    ✏️   │ ← Tap to change
│  [👤 Manually categorized]           │ ← Shows if user-corrected
│  Confidence        100%              │
│  ...                                 │
│                                      │
│  [    🔄 Change Category    ]        │ ← Opens bottom sheet
│  [    📚 Teach Pattern      ]        │
└──────────────────────────────────────┘
```

### Category Picker Bottom Sheet

```
┌──────────────────────────────────────┐
│  Select Category                     │
├──────────────────────────────────────┤
│  FOOD & DRINK                        │
│    ○ Groceries                       │
│    ● Restaurant                ✓     │ ← Selected
│    ○ Coffee                          │
│                                      │
│  TRANSPORTATION                      │
│    ○ Gas                             │
│    ○ Taxi/Rideshare                  │
│    ○ TransMilenio                    │
│                                      │
│  RENT & UTILITIES                    │
│    ○ Administración                  │
│    ○ Utilities                       │
│                                      │
│  ... (scrollable)                    │
└──────────────────────────────────────┘
```

---

## Future Enhancements

1. **Bulk categorization** — Select multiple transactions and apply same category
2. **Category suggestions** — Show AI-suggested categories based on merchant name
3. **Undo action** — Allow reverting to original automatic category
4. **Category rules** — "Always categorize [merchant] as [category]"
5. **Search in picker** — Filter categories by typing

---

## Verification

1. **Build:** `./gradlew build`
2. **Tests:** `./gradlew :shared:jvmTest`
3. **Manual testing:**
   - Open transaction detail
   - Tap "Change Category"
   - Select new category
   - Verify category updates immediately
   - Navigate back and verify category persists
   - Force close app, reopen, verify category still shows new value
   - Verify "Manually categorized" badge appears
