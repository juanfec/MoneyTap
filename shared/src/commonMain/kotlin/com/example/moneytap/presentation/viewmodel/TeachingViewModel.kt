package com.example.moneytap.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneytap.domain.Constants
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.FieldSelection
import com.example.moneytap.domain.model.FieldType
import com.example.moneytap.domain.model.LearnedBankPattern
import com.example.moneytap.domain.model.SmsMessage
import com.example.moneytap.domain.model.TeachingExample
import com.example.moneytap.domain.repository.SmsRepository
import com.example.moneytap.domain.repository.UserPatternRepository
import com.example.moneytap.domain.service.PatternInferenceEngine
import com.example.moneytap.presentation.state.TeachingStep
import com.example.moneytap.presentation.state.TeachingUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * ViewModel for the teaching flow where users define custom SMS patterns.
 *
 * Guides users through selecting fields in 2+ SMS examples, infers pattern,
 * and saves it for future matching.
 */
class TeachingViewModel(
    private val smsRepository: SmsRepository,
    private val userPatternRepository: UserPatternRepository,
    private val patternInferenceEngine: PatternInferenceEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TeachingUiState())
    val uiState: StateFlow<TeachingUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TeachingEvent>()
    val events: SharedFlow<TeachingEvent> = _events.asSharedFlow()

    /**
     * Start teaching flow with an SMS message.
     */
    fun startTeaching(sms: SmsMessage) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    currentStep = TeachingStep.SELECT_AMOUNT,
                    currentSms = sms,
                    examples = emptyList(),
                    currentSelections = emptyList(),
                    suggestedBankName = sms.sender,
                    error = null,
                )
            }

            // Load other SMS from same sender
            loadSmsFromSameSender(sms.sender)
        }
    }

    /**
     * Load SMS messages from the same sender for adding more examples.
     */
    private suspend fun loadSmsFromSameSender(sender: String) {
        smsRepository.getInboxMessages(limit = Constants.DEFAULT_SMS_LIMIT)
            .onSuccess { allMessages ->
                val fromSameSender = allMessages.filter { msg ->
                    msg.sender.equals(sender, ignoreCase = true) &&
                    msg.id != _uiState.value.currentSms?.id
                }
                _uiState.update { it.copy(availableSmsFromSameSender = fromSameSender) }
            }
            .onFailure { e ->
                _uiState.update { it.copy(error = "Failed to load SMS messages: ${e.message}") }
            }
    }

    /**
     * User selects text for a specific field type.
     */
    fun selectText(fieldType: FieldType, startIndex: Int, endIndex: Int) {
        val currentSms = _uiState.value.currentSms ?: return
        val selectedText = currentSms.body.substring(startIndex, endIndex)

        val selection = FieldSelection(
            fieldType = fieldType,
            startIndex = startIndex,
            endIndex = endIndex,
            selectedText = selectedText,
        )

        _uiState.update {
            it.copy(
                currentSelections = it.currentSelections + selection,
                error = null,
            )
        }

        // Move to next step based on current step
        advanceStep()
    }

    /**
     * Advance to the next step in the wizard.
     */
    private fun advanceStep() {
        val currentStep = _uiState.value.currentStep
        val nextStep = when (currentStep) {
            TeachingStep.SELECT_AMOUNT -> TeachingStep.SELECT_MERCHANT
            TeachingStep.SELECT_MERCHANT -> TeachingStep.SELECT_OPTIONAL_FIELDS
            TeachingStep.SELECT_OPTIONAL_FIELDS -> TeachingStep.ADD_MORE_EXAMPLES
            else -> currentStep
        }
        _uiState.update { it.copy(currentStep = nextStep) }
    }

    /**
     * Confirm current example and prepare for next step.
     */
    fun confirmCurrentExample() {
        val currentSms = _uiState.value.currentSms ?: return
        val selections = _uiState.value.currentSelections

        if (selections.isEmpty()) {
            _uiState.update { it.copy(error = "No fields selected") }
            return
        }

        val example = TeachingExample(
            id = "example_${Clock.System.now().toEpochMilliseconds()}",
            smsBody = currentSms.body,
            senderId = currentSms.sender,
            selections = selections,
            category = null,
            createdAt = Clock.System.now(),
        )

        val updatedExamples = _uiState.value.examples + example

        _uiState.update {
            it.copy(
                examples = updatedExamples,
                currentSelections = emptyList(),
                currentStep = TeachingStep.ADD_MORE_EXAMPLES,
                error = null,
            )
        }

        // If we have 2+ examples, try to infer pattern
        if (updatedExamples.size >= 2) {
            inferPattern()
        }
    }

    /**
     * Add another example SMS to improve pattern accuracy.
     */
    fun addAnotherExample(sms: SmsMessage) {
        _uiState.update {
            it.copy(
                currentSms = sms,
                currentStep = TeachingStep.SELECT_AMOUNT,
                currentSelections = emptyList(),
                error = null,
            )
        }
    }

    /**
     * Skip optional fields and proceed to add more examples.
     */
    fun skipOptionalFields() {
        confirmCurrentExample()
    }

    /**
     * Infer pattern from collected examples.
     */
    private fun inferPattern() {
        viewModelScope.launch {
            val examples = _uiState.value.examples
            if (examples.size < 2) {
                _uiState.update { it.copy(error = "Need at least 2 examples to infer pattern") }
                return@launch
            }

            try {
                val pattern = patternInferenceEngine.inferPattern(examples)
                if (pattern != null) {
                    _uiState.update {
                        it.copy(
                            inferredPattern = pattern,
                            currentStep = TeachingStep.REVIEW_PATTERN,
                            error = null,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            error = "Could not infer pattern. Examples might be too different.",
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Pattern inference failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Proceed to pattern review after collecting examples.
     */
    fun proceedToReview() {
        inferPattern()
    }

    /**
     * Set the default category for this pattern.
     */
    fun setCategory(category: Category) {
        _uiState.update {
            it.copy(
                selectedCategory = category,
                currentStep = TeachingStep.SET_CATEGORY,
                error = null,
            )
        }
    }

    /**
     * Save the learned pattern to database.
     */
    fun savePattern() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val inferredPattern = _uiState.value.inferredPattern
                val examples = _uiState.value.examples
                val category = _uiState.value.selectedCategory
                val bankName = _uiState.value.suggestedBankName

                if (inferredPattern == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "No pattern to save",
                        )
                    }
                    return@launch
                }

                if (examples.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "No examples to save",
                        )
                    }
                    return@launch
                }

                val pattern = LearnedBankPattern(
                    id = "pattern_${Clock.System.now().toEpochMilliseconds()}",
                    bankName = bankName,
                    senderIds = listOf(examples.first().senderId),
                    examples = examples,
                    inferredPattern = inferredPattern,
                    defaultCategory = category,
                    enabled = true,
                    successCount = 0,
                    failCount = 0,
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                )

                userPatternRepository.savePattern(pattern)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentStep = TeachingStep.DONE,
                        error = null,
                    )
                }

                _events.emit(TeachingEvent.PatternSaved)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to save pattern: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * Reset the teaching flow.
     */
    fun reset() {
        _uiState.update { TeachingUiState() }
    }
}

/**
 * Events emitted by TeachingViewModel.
 */
sealed class TeachingEvent {
    /** Pattern was successfully saved */
    data object PatternSaved : TeachingEvent()
}
