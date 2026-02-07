package com.example.moneytap.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.FieldType
import com.example.moneytap.domain.model.SmsMessage
import com.example.moneytap.presentation.state.TeachingStep
import com.example.moneytap.presentation.state.TeachingUiState
import com.example.moneytap.presentation.viewmodel.TeachingEvent
import com.example.moneytap.presentation.viewmodel.TeachingViewModel
import com.example.moneytap.ui.component.SelectableText

/**
 * Main teaching screen that guides users through defining a custom SMS pattern.
 *
 * Multi-step wizard flow:
 * 1. Select SMS
 * 2. Select amount field
 * 3. Select merchant field
 * 4. Select optional fields (balance, card, etc.)
 * 5. Add more examples (recommended 2-3)
 * 6. Review inferred pattern
 * 7. Set default category
 * 8. Done
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachingScreen(
    viewModel: TeachingViewModel,
    initialSms: SmsMessage?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                TeachingEvent.PatternSaved -> onNavigateBack()
            }
        }
    }

    LaunchedEffect(initialSms) {
        initialSms?.let { viewModel.startTeaching(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Teach Pattern") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        TeachingContent(
            uiState = uiState,
            onSelectText = viewModel::selectText,
            onConfirmExample = viewModel::confirmCurrentExample,
            onAddExample = viewModel::addAnotherExample,
            onSkipOptionalFields = viewModel::skipOptionalFields,
            onProceedToReview = viewModel::proceedToReview,
            onSetCategory = viewModel::setCategory,
            onSavePattern = viewModel::savePattern,
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
private fun TeachingContent(
    uiState: TeachingUiState,
    onSelectText: (FieldType, Int, Int) -> Unit,
    onConfirmExample: () -> Unit,
    onAddExample: (SmsMessage) -> Unit,
    onSkipOptionalFields: () -> Unit,
    onProceedToReview: () -> Unit,
    onSetCategory: (Category) -> Unit,
    onSavePattern: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // Progress indicator
        StepProgress(
            currentStep = uiState.currentStep,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Error message
        uiState.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        // Loading indicator
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return
        }

        // Step content
        when (uiState.currentStep) {
            TeachingStep.SELECT_SMS -> {
                Text("Please select an SMS from the list")
            }

            TeachingStep.SELECT_AMOUNT -> {
                SelectFieldStep(
                    uiState = uiState,
                    fieldType = FieldType.AMOUNT,
                    instruction = "Select the amount in the SMS by tapping and dragging",
                    onSelectText = onSelectText,
                    onConfirm = onConfirmExample,
                )
            }

            TeachingStep.SELECT_MERCHANT -> {
                SelectFieldStep(
                    uiState = uiState,
                    fieldType = FieldType.MERCHANT,
                    instruction = "Select the merchant name",
                    onSelectText = onSelectText,
                    onConfirm = onConfirmExample,
                )
            }

            TeachingStep.SELECT_OPTIONAL_FIELDS -> {
                OptionalFieldsStep(
                    uiState = uiState,
                    onSelectText = onSelectText,
                    onSkip = onSkipOptionalFields,
                    onConfirm = onConfirmExample,
                )
            }

            TeachingStep.ADD_MORE_EXAMPLES -> {
                AddMoreExamplesStep(
                    uiState = uiState,
                    onAddExample = onAddExample,
                    onProceedToReview = onProceedToReview,
                )
            }

            TeachingStep.REVIEW_PATTERN -> {
                ReviewPatternStep(
                    uiState = uiState,
                    onProceed = { onSetCategory(Category.UNCATEGORIZED) },
                )
            }

            TeachingStep.SET_CATEGORY -> {
                SetCategoryStep(
                    onSetCategory = onSetCategory,
                    onSave = onSavePattern,
                )
            }

            TeachingStep.DONE -> {
                Text(
                    "Pattern saved successfully!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun StepProgress(
    currentStep: TeachingStep,
    modifier: Modifier = Modifier,
) {
    val steps = TeachingStep.entries
    val currentIndex = steps.indexOf(currentStep)
    val progress = (currentIndex + 1).toFloat() / steps.size

    Column(modifier = modifier) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Step ${currentIndex + 1} of ${steps.size}: ${currentStep.name.lowercase().replace('_', ' ')}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectFieldStep(
    uiState: TeachingUiState,
    fieldType: FieldType,
    instruction: String,
    onSelectText: (FieldType, Int, Int) -> Unit,
    onConfirm: () -> Unit,
) {
    Column {
        Text(
            text = instruction,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        uiState.currentSms?.let { sms ->
            SelectableText(
                text = sms.body,
                existingSelections = uiState.currentSelections,
                currentFieldType = fieldType,
                onSelectionMade = { start, end, text ->
                    onSelectText(fieldType, start, end)
                },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        Button(
            onClick = onConfirm,
            enabled = uiState.currentSelections.any { it.fieldType == fieldType },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun OptionalFieldsStep(
    uiState: TeachingUiState,
    onSelectText: (FieldType, Int, Int) -> Unit,
    onSkip: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column {
        Text(
            text = "Select optional fields (balance, card number, etc.)",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        uiState.currentSms?.let { sms ->
            SelectableText(
                text = sms.body,
                existingSelections = uiState.currentSelections,
                currentFieldType = FieldType.BALANCE,
                onSelectionMade = { start, end, text ->
                    onSelectText(FieldType.BALANCE, start, end)
                },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f),
            ) {
                Text("Skip")
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            ) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun AddMoreExamplesStep(
    uiState: TeachingUiState,
    onAddExample: (SmsMessage) -> Unit,
    onProceedToReview: () -> Unit,
) {
    Column {
        Text(
            text = "Add ${if (uiState.examples.size < 2) "at least one more" else "another"} example (recommended: 3 total)",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Text(
            text = "Examples collected: ${uiState.examples.size}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Text(
            text = "Available SMS from ${uiState.suggestedBankName}:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(uiState.availableSmsFromSameSender) { sms ->
                Card(
                    onClick = { onAddExample(sms) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = sms.body,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onProceedToReview,
            enabled = uiState.examples.size >= 2,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Review Pattern")
        }
    }
}

@Composable
private fun ReviewPatternStep(
    uiState: TeachingUiState,
    onProceed: () -> Unit,
) {
    Column {
        Text(
            text = "Pattern Review",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        uiState.inferredPattern?.let { pattern ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Confidence: ${(pattern.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Examples used: ${uiState.examples.size}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Button(
            onClick = onProceed,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Set Category")
        }
    }
}

@Composable
private fun SetCategoryStep(
    onSetCategory: (Category) -> Unit,
    onSave: () -> Unit,
) {
    Column {
        Text(
            text = "Set Default Category",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Text(
            text = "Choose a default category for transactions matching this pattern:",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(Category.entries.filter { it != Category.UNCATEGORIZED }) { category ->
                Card(
                    onClick = { onSetCategory(category) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = category.name.replace('_', ' '),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Pattern")
        }
    }
}
