# Feature: User-Defined Parsers & Categorization (Teach by Example)

## Overview

Allow users to teach the app to recognize new bank SMS formats and categorization rules by selecting examples. The system learns patterns from 2-3 examples and uses fuzzy matching to apply them to future messages.

## Architecture Summary

```
┌─────────────────────────────────────────────────────────────┐
│                     TEACHING FLOW                           │
│  User selects 2-3 SMS examples → highlights fields →       │
│  system infers pattern → saves for future matching          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   PATTERN MATCHING                          │
│  New SMS arrives → try user patterns first (fuzzy match) → │
│  fallback to built-in parsers → categorize                 │
└─────────────────────────────────────────────────────────────┘
```

### Integration Points (Existing Code)

| Component | Location | How We Integrate |
|-----------|----------|-----------------|
| `BankSmsParserFactory` | `data/parser/BankSmsParserFactory.kt` | Keep as `object`. User patterns tried at use case level **before** built-in parsers. |
| `ParseSmsTransactionsUseCase` | `domain/usecase/ParseSmsTransactionsUseCase.kt` | **Modify** to accept `UserPatternRepository` and try user patterns first. |
| `CategorizationEngine` | `domain/service/CategorizationEngine.kt` | **Modify** with optional `userRules` param (default empty) to add Layer 0. Preserves no-arg constructor. |
| `StringSimilarity` | `util/StringSimilarity.kt` | **Extend** with `longestCommonSubstring()`. |
| `AmountParser` | `data/parser/AmountParser.kt` | **Extend** with `detectFormat()` and `formatForDisplay()`. |
| `MatchType` | `domain/model/CategorizedTransaction.kt` | **Add** `USER_PATTERN` and `USER_RULE` entries. |

---

## PHASE 1: Data Models ✅ COMPLETED

### Location: `shared/src/commonMain/kotlin/com/example/moneytap/domain/model/`

> Note: Uses flat `domain/model/` directory (project convention).
> Also added `kotlinSerialization` plugin and `kotlinx-serialization-json` dependency to `shared/build.gradle.kts`.

### Task 1.1: Create FieldType enum

File: `FieldType.kt`

```kotlin
enum class FieldType {
    AMOUNT,
    MERCHANT,
    BALANCE,
    CARD_LAST_4,
    DATE,
    TRANSACTION_TYPE,
}
```

### Task 1.2: Create FieldSelection data class

File: `FieldSelection.kt`

```kotlin
@Serializable
data class FieldSelection(
    val fieldType: FieldType,
    val startIndex: Int,
    val endIndex: Int,
    val selectedText: String,
)
```

### Task 1.3: Create TeachingExample data class

File: `TeachingExample.kt`

```kotlin
@Serializable
data class TeachingExample(
    val id: String,
    val smsBody: String,
    val senderId: String,
    val selections: List<FieldSelection>,
    val category: Category?,
    val createdAt: Instant,
)
```

### Task 1.4: Create AmountFormat data class

File: `AmountFormat.kt`

```kotlin
@Serializable
data class AmountFormat(
    val thousandsSeparator: Char,
    val decimalSeparator: Char,
    val currencySymbol: String?,
    val currencyPosition: CurrencyPosition,
)

@Serializable
enum class CurrencyPosition { BEFORE, AFTER, NONE }
```

### Task 1.5: Create InferredPattern data class

File: `InferredPattern.kt`

```kotlin
@Serializable
data class InferredPattern(
    val segments: List<PatternSegment>,
    val amountFormat: AmountFormat,
    val confidence: Double,  // Double, not Float — matches project convention
)

@Serializable
sealed class PatternSegment {
    @Serializable
    data class FixedText(val text: String, val fuzzyMatch: Boolean = true) : PatternSegment()
    @Serializable
    data class Variable(val fieldType: FieldType) : PatternSegment()
}
```

### Task 1.6: Create LearnedBankPattern data class

File: `LearnedBankPattern.kt`

```kotlin
@Serializable
data class LearnedBankPattern(
    val id: String,
    val bankName: String,
    val senderIds: List<String>,
    val examples: List<TeachingExample>,
    val inferredPattern: InferredPattern,
    val defaultCategory: Category?,
    val enabled: Boolean = true,
    val successCount: Int = 0,
    val failCount: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

### Task 1.7: Create UserCategorizationRule data class

File: `UserCategorizationRule.kt`

```kotlin
@Serializable
data class UserCategorizationRule(
    val id: String,
    val name: String,
    val conditions: List<RuleCondition>,
    val category: Category,
    val priority: Int,
    val learnedFromExamples: List<String>?,
    val enabled: Boolean = true,
    val createdAt: Instant,
)

@Serializable
sealed class RuleCondition {
    @Serializable
    data class MerchantContains(val keyword: String) : RuleCondition()
    @Serializable
    data class MerchantEquals(val name: String) : RuleCondition()
    @Serializable
    data class SenderContains(val keyword: String) : RuleCondition()
    @Serializable
    data class AmountRange(val min: Double?, val max: Double?) : RuleCondition()
    @Serializable
    data class AnyKeyword(val keywords: List<String>) : RuleCondition()
}
```

### Task 1.8: Create PatternMatchResult data class

File: `PatternMatchResult.kt` (renamed from `MatchResult` to avoid confusion with `MatchType`)

```kotlin
data class PatternMatchResult(
    val extractedFields: Map<FieldType, String>,
    val confidence: Double,
    val patternId: String,
)
```

### Task 1.9: Update existing MatchType enum

File: `CategorizedTransaction.kt` (modify existing)

```kotlin
enum class MatchType { EXACT, FUZZY, KEYWORD, USER_PATTERN, USER_RULE, DEFAULT }
```

---

## PHASE 2: Database Schema (SQLDelight) ✅ COMPLETED

### Location: `shared/src/commonMain/sqldelight/com/example/moneytap/data/database/`

> Same directory as existing `Transaction.sq`. Same `MoneyTapDatabase`.

### Task 2.1: Create migration file

File: `1.sqm` (SQLDelight migration from v1 → v2)

### Task 2.2: Add UserBankPattern table

File: `UserBankPattern.sq`

```sql
CREATE TABLE UserBankPatternEntity (
    id TEXT PRIMARY KEY,
    bankName TEXT NOT NULL,
    senderIds TEXT NOT NULL,         -- JSON: List<String>
    inferredPattern TEXT NOT NULL,   -- JSON: InferredPattern
    defaultCategory TEXT,
    enabled INTEGER AS Boolean DEFAULT 1,
    successCount INTEGER DEFAULT 0,
    failCount INTEGER DEFAULT 0,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);

insertPattern:
INSERT OR REPLACE INTO UserBankPatternEntity(id, bankName, senderIds, inferredPattern, defaultCategory, enabled, successCount, failCount, createdAt, updatedAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

getAll:
SELECT * FROM UserBankPatternEntity WHERE enabled = 1;

getById:
SELECT * FROM UserBankPatternEntity WHERE id = ?;

getBySenderId:
SELECT * FROM UserBankPatternEntity WHERE enabled = 1 AND senderIds LIKE '%' || ? || '%';

updateStats:
UPDATE UserBankPatternEntity SET successCount = ?, failCount = ?, updatedAt = ? WHERE id = ?;

deletePattern:
DELETE FROM UserBankPatternEntity WHERE id = ?;
```

### Task 2.3: Add TeachingExample table

File: `TeachingExample.sq`

```sql
CREATE TABLE TeachingExampleEntity (
    id TEXT PRIMARY KEY,
    bankPatternId TEXT NOT NULL,
    smsBody TEXT NOT NULL,
    senderId TEXT NOT NULL,
    selections TEXT NOT NULL,   -- JSON: List<FieldSelection>
    category TEXT,
    createdAt INTEGER NOT NULL,
    FOREIGN KEY (bankPatternId) REFERENCES UserBankPatternEntity(id) ON DELETE CASCADE
);

insertExample:
INSERT INTO TeachingExampleEntity(id, bankPatternId, smsBody, senderId, selections, category, createdAt)
VALUES (?, ?, ?, ?, ?, ?, ?);

getByPatternId:
SELECT * FROM TeachingExampleEntity WHERE bankPatternId = ?;

deleteExample:
DELETE FROM TeachingExampleEntity WHERE id = ?;
```

### Task 2.4: Add UserCategorizationRule table

File: `UserCategorizationRule.sq`

```sql
CREATE TABLE UserCategorizationRuleEntity (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    conditions TEXT NOT NULL,       -- JSON: List<RuleCondition>
    category TEXT NOT NULL,
    priority INTEGER DEFAULT 100,
    learnedFromExamples TEXT,       -- JSON: List<String>?
    enabled INTEGER AS Boolean DEFAULT 1,
    createdAt INTEGER NOT NULL
);

insertRule:
INSERT OR REPLACE INTO UserCategorizationRuleEntity(id, name, conditions, category, priority, learnedFromExamples, enabled, createdAt)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);

getAllEnabled:
SELECT * FROM UserCategorizationRuleEntity WHERE enabled = 1 ORDER BY priority DESC;

getById:
SELECT * FROM UserCategorizationRuleEntity WHERE id = ?;

deleteRule:
DELETE FROM UserCategorizationRuleEntity WHERE id = ?;

updatePriority:
UPDATE UserCategorizationRuleEntity SET priority = ? WHERE id = ?;
```

---

## PHASE 3: Repository Layer ✅ COMPLETED

### Interfaces: `shared/src/commonMain/kotlin/com/example/moneytap/domain/repository/`
### Implementations: `shared/src/commonMain/kotlin/com/example/moneytap/data/repository/`

### Task 3.1: Create UserPatternRepository interface

File: `domain/repository/UserPatternRepository.kt`

```kotlin
interface UserPatternRepository {
    suspend fun savePattern(pattern: LearnedBankPattern)
    suspend fun getAllPatterns(): List<LearnedBankPattern>
    suspend fun getPatternBySenderId(senderId: String): LearnedBankPattern?
    suspend fun updatePatternStats(id: String, successCount: Int, failCount: Int)
    suspend fun deletePattern(id: String)
    suspend fun saveTeachingExample(example: TeachingExample, patternId: String)
    suspend fun getExamplesForPattern(patternId: String): List<TeachingExample>
}
```

### Task 3.2: Create UserRuleRepository interface

File: `domain/repository/UserRuleRepository.kt`

```kotlin
interface UserRuleRepository {
    suspend fun saveRule(rule: UserCategorizationRule)
    suspend fun getEnabledRules(): List<UserCategorizationRule>
    suspend fun deleteRule(id: String)
    suspend fun updateRulePriority(id: String, priority: Int)
}
```

### Task 3.3: Implement UserPatternRepositoryImpl

File: `data/repository/UserPatternRepositoryImpl.kt`

Uses `MoneyTapDatabase` queries + `kotlinx.serialization.json.Json` for encoding/decoding:
- `senderIds: List<String>` ↔ JSON TEXT
- `inferredPattern: InferredPattern` ↔ JSON TEXT
- `selections: List<FieldSelection>` ↔ JSON TEXT

### Task 3.4: Implement UserRuleRepositoryImpl

File: `data/repository/UserRuleRepositoryImpl.kt`

Uses `MoneyTapDatabase` queries + `kotlinx.serialization.json.Json` for encoding/decoding:
- `conditions: List<RuleCondition>` ↔ JSON TEXT
- `learnedFromExamples: List<String>?` ↔ JSON TEXT

### Dependency Addition

Add to `shared/build.gradle.kts` commonMain:
```kotlin
implementation(libs.kotlinx.serialization.json)
```

Add to `gradle/libs.versions.toml`:
```toml
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "..." }
```

Add `kotlin("plugin.serialization")` to shared module plugins.

---

## PHASE 4: Pattern Inference Engine ✅ COMPLETED

### Location: `shared/src/commonMain/kotlin/com/example/moneytap/domain/service/`

### Task 4.1: Extend StringSimilarity (existing file)

File: `shared/src/commonMain/kotlin/com/example/moneytap/util/StringSimilarity.kt`

Add to existing `object StringSimilarity`:
```kotlin
fun longestCommonSubstring(s1: String, s2: String): String
```

### Task 4.2: Create PatternInferenceEngine

File: `domain/service/PatternInferenceEngine.kt`

**Responsibilities:**
1. Take 2+ TeachingExamples as input
2. Align examples by their field selections
3. Find common fixed text between variable fields (anchors)
4. Detect amount format (thousands/decimal separators, currency symbol)
5. Calculate confidence score
6. Return InferredPattern

**Algorithm outline:**

```
1. Validate: require at least 2 examples with same fields selected
2. Sort selections by startIndex for each example
3. For each position between fields:
   a. Extract text from all examples at that position
   b. Find longest common substring (LCS)
   c. Store as FixedText segment
4. Analyze amount selections to detect AmountFormat
5. Calculate confidence = (sum of LCS lengths) / (avg message length)
6. Return InferredPattern with segments and format
```

---

## PHASE 5: Fuzzy Pattern Matcher ✅ COMPLETED

### Location: `shared/src/commonMain/kotlin/com/example/moneytap/domain/service/`

### Task 5.1: Extend AmountParser (existing file)

File: `shared/src/commonMain/kotlin/com/example/moneytap/data/parser/AmountParser.kt`

Add to existing `object AmountParser`:
```kotlin
fun detectFormat(amountStrings: List<String>): AmountFormat
fun formatForDisplay(amount: Double, format: AmountFormat): String
```

### Task 5.2: Create FuzzyPatternMatcher

File: `domain/service/FuzzyPatternMatcher.kt`

**Configuration:**
- `minConfidenceThreshold: Double = 0.65`
- `fuzzyTextThreshold: Double = 0.75` (for fixed text matching)

**Algorithm outline:**

```
1. Start at position 0 in SMS
2. For each segment in pattern:
   a. If FixedText:
      - Try exact match (case-insensitive)
      - If not found and fuzzyMatch=true, try Levenshtein similarity
      - If similarity < threshold, return null (no match)
      - Advance position to end of matched text
   b. If Variable:
      - Look ahead to find next FixedText segment
      - Extract text between current position and next anchor
      - Parse based on fieldType (amount, merchant, etc.)
      - Store in extractedFields map
3. Calculate average confidence from all segment matches
4. If avgConfidence >= minConfidenceThreshold, return PatternMatchResult
5. Otherwise return null
```

---

## PHASE 6: Category Teaching Engine ✅ COMPLETED

### Location: `shared/src/commonMain/kotlin/com/example/moneytap/domain/service/`

### Task 6.1: Create CategoryTeachingEngine

File: `domain/service/CategoryTeachingEngine.kt`

**Algorithm outline:**

```
1. Extract merchants from all examples, normalize (uppercase, remove special chars)
2. Tokenize each merchant into words
3. Find tokens common to ALL merchants
4. Filter out generic words (DE, LA, SAS, LTDA, etc.)
5. Build conditions:
   a. If exact same merchant in all: MerchantEquals
   b. Else if common keywords found: AnyKeyword
   c. If same sender in all: SenderContains
6. Return UserCategorizationRule with conditions and category
```

---

## PHASE 7: Integration with Existing Code ✅ COMPLETED

### Task 7.1: Modify ParseSmsTransactionsUseCase

File: `shared/src/commonMain/kotlin/com/example/moneytap/domain/usecase/ParseSmsTransactionsUseCase.kt`

**Changes:**
- Add optional `UserPatternRepository` constructor param (default `null`)
- Add `FuzzyPatternMatcher` param (default `null`)
- In `parseMessage()`, try user patterns FIRST before `BankSmsParserFactory`
- Track success/failure stats for user patterns

```kotlin
class ParseSmsTransactionsUseCase(
    private val smsRepository: SmsRepository,
    private val userPatternRepository: UserPatternRepository? = null,
    private val fuzzyMatcher: FuzzyPatternMatcher? = null,
) {
    fun parseMessage(sms: SmsMessage): TransactionInfo? {
        // 1. Try user patterns first (if available)
        if (userPatternRepository != null && fuzzyMatcher != null) {
            // ... try matching user patterns
        }

        // 2. Built-in parsers (existing logic, unchanged)
        val specificParser = BankSmsParserFactory.getParser(sms.sender)
        // ...
    }
}
```

> **Why optional params?** Preserves backward compatibility with existing tests using `ParseSmsTransactionsUseCase(fakeSmsRepository)`.

### Task 7.2: Modify CategorizationEngine

File: `shared/src/commonMain/kotlin/com/example/moneytap/domain/service/CategorizationEngine.kt`

**Changes:**
- Add optional `userRules: List<UserCategorizationRule>` constructor param (default `emptyList()`)
- Add Layer 0 (before exact match): check user rules first
- No-arg constructor still works → existing tests pass unchanged

```kotlin
class CategorizationEngine(
    private val userRules: List<UserCategorizationRule> = emptyList(),
) {
    fun categorize(transaction: TransactionInfo): CategorizedTransaction {
        // Layer 0: User rules (highest priority)
        if (userRules.isNotEmpty()) {
            matchUserRule(transaction)?.let { return it }
        }

        // Layers 1-5: existing logic unchanged
        // ...
    }
}
```

### Integration Tests for Phase 7 ✅ COMPLETED

Created integration tests to verify Phase 7 modifications:

**File: `shared/src/commonTest/kotlin/com/example/moneytap/domain/usecase/ParseSmsTransactionsWithUserPatternsTest.kt`**
- 7 test cases covering user pattern integration
- Tests verify user patterns are tried before built-in parsers
- Tests verify fallback to built-in parsers when no pattern matches
- Tests verify success/failure statistics are updated correctly
- Tests verify backward compatibility (works without user pattern repository)

**File: `shared/src/commonTest/kotlin/com/example/moneytap/domain/service/CategorizationEngineWithUserRulesTest.kt`**
- 8 test cases covering user rule integration
- Tests verify user rules take highest priority (Layer 0)
- Tests verify fallback to built-in layers works correctly
- Tests verify backward compatibility (no-arg constructor)
- Tests verify rule priority ordering, condition types, and disabled rules

**File: `shared/src/commonTest/kotlin/com/example/moneytap/testutil/FakeUserPatternRepository.kt`**
- In-memory fake repository for testing user patterns

All 166 tests passing ✅

### Task 7.3: Update DI Modules

File: `composeApp/src/commonMain/kotlin/com/example/moneytap/di/CategorizationModule.kt`

```kotlin
val categorizationModule = module {
    // Existing bindings (unchanged)
    singleOf(::CategorizationEngine)
    factoryOf(::CategorizeTransactionsUseCase)
    factoryOf(::GetSpendingByCategoryUseCase)
    factoryOf(::SpendingViewModel)

    // New bindings
    singleOf(::PatternInferenceEngine)
    singleOf(::FuzzyPatternMatcher)
    singleOf(::CategoryTeachingEngine)
    factoryOf(::TeachingViewModel)
}
```

File: `composeApp/src/commonMain/kotlin/com/example/moneytap/di/DatabaseModule.kt`

```kotlin
val databaseModule = module {
    // Existing bindings (unchanged)
    single { ... MoneyTapDatabase ... }
    single<TransactionRepository> { TransactionRepositoryImpl(get()) }

    // New bindings
    single<UserPatternRepository> { UserPatternRepositoryImpl(get()) }
    single<UserRuleRepository> { UserRuleRepositoryImpl(get()) }
}
```

---

## PHASE 8: UI Components (Compose Multiplatform) ✅ COMPLETED

### Location: `composeApp/src/commonMain/kotlin/com/example/moneytap/`

> Multiplatform UI — NOT Android-only. All screens in `composeApp/src/commonMain/`.

### Task 8.1: Create TeachingUiState

File: `presentation/state/TeachingUiState.kt`

```kotlin
data class TeachingUiState(
    val currentStep: TeachingStep,
    val currentSms: SmsMessage?,
    val examples: List<TeachingExample>,
    val currentSelections: List<FieldSelection>,
    val availableSmsFromSameSender: List<SmsMessage>,
    val inferredPattern: InferredPattern?,
    val suggestedBankName: String,
    val selectedCategory: Category?,
    val error: String?,
)

enum class TeachingStep {
    SELECT_SMS,
    SELECT_AMOUNT,
    SELECT_MERCHANT,
    SELECT_OPTIONAL_FIELDS,
    ADD_MORE_EXAMPLES,
    REVIEW_PATTERN,
    SET_CATEGORY,
    DONE,
}
```

### Task 8.2: Create TeachingViewModel

File: `presentation/viewmodel/TeachingViewModel.kt`

Pattern: Extends `ViewModel()`, exposes `StateFlow<TeachingUiState>` and `SharedFlow<TeachingEvent>`.

**Actions:**
- `startTeaching(sms: SmsMessage)`
- `selectText(fieldType: FieldType, startIndex: Int, endIndex: Int)`
- `confirmCurrentExample()`
- `addAnotherExample(sms: SmsMessage)`
- `skipOptionalFields()`
- `setCategory(category: Category)`
- `savePattern()`

### Task 8.3: Create SelectableText component

File: `ui/component/SelectableText.kt`

```kotlin
@Composable
fun SelectableText(
    text: String,
    existingSelections: List<FieldSelection>,
    currentFieldType: FieldType,
    onSelectionMade: (startIndex: Int, endIndex: Int, selectedText: String) -> Unit,
    modifier: Modifier = Modifier,
)
```

### Task 8.4: Create TeachingScreen

File: `ui/screen/TeachingScreen.kt`

Multi-step wizard UI with progress indicator.

### Task 8.5: Create CategoryTeachingScreen

File: `ui/screen/CategoryTeachingScreen.kt`

Simpler flow: select transactions → pick category → confirm rule.

### Task 8.6: Update Navigation

File: `navigation/Routes.kt`

```kotlin
sealed interface Route {
    // ... existing routes
    @Serializable data object Teaching : Route
    @Serializable data object CategoryTeaching : Route
}
```

File: `App.kt` — add composable destinations for new routes.

### Phase 8 Implementation Summary ✅

**Task 8.1**: TeachingUiState created with TeachingStep enum
- Location: `composeApp/src/commonMain/kotlin/com/example/moneytap/presentation/state/TeachingUiState.kt`

**Task 8.2**: TeachingViewModel created with full wizard flow logic
- Location: `composeApp/src/commonMain/kotlin/com/example/moneytap/presentation/viewmodel/TeachingViewModel.kt`
- Actions: startTeaching, selectText, confirmExample, addExample, skipOptionalFields, setCategory, savePattern

**Task 8.3**: SelectableText component created
- Location: `composeApp/src/commonMain/kotlin/com/example/moneytap/ui/component/SelectableText.kt`
- Features: Text selection with highlights, existing selections display, field type colors

**Task 8.4**: TeachingScreen created
- Location: `composeApp/src/commonMain/kotlin/com/example/moneytap/ui/screen/TeachingScreen.kt`
- Multi-step wizard with progress indicator
- Steps: SELECT_SMS → SELECT_AMOUNT → SELECT_MERCHANT → SELECT_OPTIONAL_FIELDS → ADD_MORE_EXAMPLES → REVIEW_PATTERN → SET_CATEGORY → DONE

**Task 8.5**: CategoryTeachingScreen created
- Location: `composeApp/src/commonMain/kotlin/com/example/moneytap/ui/screen/CategoryTeachingScreen.kt`
- Simpler flow: Select transactions → Pick category → Review & save

**Task 8.6**: Navigation updated
- Routes.kt: Added Teaching and CategoryTeaching routes
- App.kt: Added composable destinations for both screens

**Task 7.3 (completed during Phase 8)**: DI Modules updated
- CategorizationModule.kt: Added PatternInferenceEngine, FuzzyPatternMatcher, CategoryTeachingEngine, TeachingViewModel
- DatabaseModule.kt: Added UserPatternRepository and UserRuleRepository bindings

---

## PHASE 9: Testing

### Location: `shared/src/commonTest/kotlin/com/example/moneytap/`
### JVM-only: `shared/src/jvmTest/kotlin/com/example/moneytap/`

### Task 9.1: PatternInferenceEngineTest (`commonTest/.../domain/service/`) ✅ ALREADY CREATED

Test cases implemented:
- Two simple examples with amount and merchant ✅
- Examples with different merchant lengths ✅
- Examples with balance field ✅
- Colombian amount format detection ✅
- Edge case: only one example (should fail) ✅
- Edge case: examples with different fields selected (should fail) ✅
- Confidence increases with more examples ✅

### Task 9.2: FuzzyPatternMatcherTest (`commonTest/.../domain/service/`) ✅ ALREADY CREATED

Test cases implemented:
- Exact match returns high confidence ✅
- Minor typo still matches (fuzzy) ✅
- Completely different SMS returns null ✅
- Amount extraction with Colombian format ✅
- Merchant extraction with various end markers ✅
- Confidence threshold enforcement ✅
- Handles pattern with no fixed text ✅
- Handles multiple variable fields in sequence ✅
- Fuzzy matching disabled requires exact match ✅
- Extracts card last 4 digits ✅

### Task 9.3: CategoryTeachingEngineTest (`commonTest/.../domain/service/`) ✅ ALREADY CREATED

Test cases implemented:
- Two transactions same merchant → MerchantEquals rule ✅
- Two transactions different merchants, common keyword → AnyKeyword rule ✅
- Filters generic words correctly ✅
- Handles empty merchant names ✅
- Returns null for single transaction ✅
- Adds sender condition when all from same sender ✅
- matchesRule returns true when all conditions match ✅
- matchesRule returns false when merchant does not match ✅
- matchesRule returns false when rule is disabled ✅
- matchesRule with AnyKeyword condition ✅
- Generates descriptive rule name ✅

### Task 9.4: UserPatternRepositoryImplTest (`jvmTest/.../data/repository/`) — TODO

Uses in-memory `JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)` — same pattern as `TransactionRepositoryImplTest`.

**Note:** This test should be created after Phase 7 (Integration) is complete.

### Task 9.5: UserRuleRepositoryImplTest (`jvmTest/.../data/repository/`) — TODO

Same in-memory SQLite pattern.

**Note:** This test should be created after Phase 7 (Integration) is complete.

### Task 9.6: Fake implementations (`commonTest/.../testutil/`) — TODO

- `FakeUserPatternRepository` — in-memory, follows `FakeTransactionRepository` pattern
- `FakeUserRuleRepository` — in-memory

**Note:** These fakes should be created when needed for integration tests or use case tests in Phase 7.

---

## Implementation Order

1. ~~**Phase 1** — Data Models (foundation)~~ ✅ DONE
2. ~~**Phase 2** — Database Schema + migration~~ ✅ DONE
3. ~~**Phase 3** — Repository Layer (requires `kotlinx-serialization-json` dependency)~~ ✅ DONE
4. ~~**Phase 4** — Pattern Inference Engine + extend `StringSimilarity`~~ ✅ DONE
5. ~~**Phase 5** — Fuzzy Pattern Matcher + extend `AmountParser`~~ ✅ DONE
6. ~~**Phase 6** — Category Teaching Engine~~ ✅ DONE
7. ~~**Phase 7** — Integration (modify existing use case + engine + DI)~~ ✅ DONE
8. **Phase 9** — Tests (before UI, to validate business logic)
9. **Phase 8** — UI Components + Navigation

> Note: Tests (Phase 9) moved before UI (Phase 8) to validate all business logic first.

---

## Key Technical Decisions

1. **kotlinx.serialization** for storing complex types in SQLite TEXT columns
2. **Levenshtein distance** (existing `StringSimilarity`) with 75% similarity threshold for fuzzy text matching
3. **Minimum 2 examples** required, recommend 3
4. **65% confidence threshold** minimum to accept a pattern match
5. **User rules priority**: Layer 0 in `CategorizationEngine`, before all built-in logic
6. **Optional constructor params** for backward compatibility (existing tests pass unchanged)
7. **`Double` for all confidence values** (project convention)
8. **Multiplatform UI** — all screens in `composeApp/src/commonMain/`, not Android-only

---

## Notes for Implementation

- Keep all pattern logic in `commonMain` (shared) — only platform-specific code in `<target>Main`
- Use `expect`/`actual` if platform-specific string handling is needed
- The SelectableText component is the trickiest UI piece — consider using `BasicText` with `AnnotatedString`
- For MVP, amount and merchant are required fields; balance, card, date are optional
- Store raw examples so patterns can be re-inferred if algorithm improves

---

## Verification

1. **Existing tests still pass:** `.\gradlew.bat :shared:jvmTest` (125 tests, 0 failures)
2. **New tests pass:** `.\gradlew.bat :shared:jvmTest` (expanded suite)
3. **Build:** `.\gradlew.bat :composeApp:assembleDebug`
4. **Manual testing:**
   - Teach a new bank pattern with 2 SMS examples
   - Verify new SMS from same sender is parsed using learned pattern
   - Teach a categorization rule
   - Verify rule takes priority over built-in categorization
