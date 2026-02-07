package com.example.moneytap.presentation.state

import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.FieldSelection
import com.example.moneytap.domain.model.InferredPattern
import com.example.moneytap.domain.model.SmsMessage
import com.example.moneytap.domain.model.TeachingExample

/**
 * UI state for the teaching flow where users define custom SMS patterns.
 */
data class TeachingUiState(
    val currentStep: TeachingStep = TeachingStep.SELECT_SMS,
    val currentSms: SmsMessage? = null,
    val examples: List<TeachingExample> = emptyList(),
    val currentSelections: List<FieldSelection> = emptyList(),
    val availableSmsFromSameSender: List<SmsMessage> = emptyList(),
    val inferredPattern: InferredPattern? = null,
    val suggestedBankName: String = "",
    val selectedCategory: Category? = null,
    val error: String? = null,
    val isLoading: Boolean = false,
)

/**
 * Steps in the teaching wizard flow.
 */
enum class TeachingStep {
    /** Select initial SMS to start teaching */
    SELECT_SMS,

    /** Select amount field in the SMS */
    SELECT_AMOUNT,

    /** Select merchant field in the SMS */
    SELECT_MERCHANT,

    /** Select optional fields (balance, card, etc.) */
    SELECT_OPTIONAL_FIELDS,

    /** Add more examples or proceed to review */
    ADD_MORE_EXAMPLES,

    /** Review the inferred pattern */
    REVIEW_PATTERN,

    /** Set default category for this pattern */
    SET_CATEGORY,

    /** Pattern saved successfully */
    DONE,
}
