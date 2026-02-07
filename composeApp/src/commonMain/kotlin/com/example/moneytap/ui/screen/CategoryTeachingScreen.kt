package com.example.moneytap.ui.screen

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.Category

/**
 * Simpler teaching screen for learning categorization rules from transaction examples.
 *
 * Flow:
 * 1. User selects 2+ transactions with same merchant/pattern
 * 2. User picks the correct category
 * 3. System learns a rule and saves it
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTeachingScreen(
    transactions: List<CategorizedTransaction>,
    onSaveRule: (List<CategorizedTransaction>, Category) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedTransactions = remember { mutableStateListOf<CategorizedTransaction>() }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var currentStep by remember { mutableStateOf(CategoryTeachingStep.SELECT_TRANSACTIONS) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Teach Category Rule") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
        ) {
            when (currentStep) {
                CategoryTeachingStep.SELECT_TRANSACTIONS -> {
                    SelectTransactionsStep(
                        transactions = transactions,
                        selectedTransactions = selectedTransactions,
                        onToggleTransaction = { transaction ->
                            if (selectedTransactions.contains(transaction)) {
                                selectedTransactions.remove(transaction)
                            } else {
                                selectedTransactions.add(transaction)
                            }
                        },
                        onContinue = {
                            currentStep = CategoryTeachingStep.SELECT_CATEGORY
                        },
                    )
                }

                CategoryTeachingStep.SELECT_CATEGORY -> {
                    SelectCategoryStep(
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        onBack = {
                            currentStep = CategoryTeachingStep.SELECT_TRANSACTIONS
                        },
                        onConfirm = {
                            currentStep = CategoryTeachingStep.REVIEW_RULE
                        },
                    )
                }

                CategoryTeachingStep.REVIEW_RULE -> {
                    ReviewRuleStep(
                        selectedTransactions = selectedTransactions,
                        selectedCategory = selectedCategory,
                        onBack = {
                            currentStep = CategoryTeachingStep.SELECT_CATEGORY
                        },
                        onSave = {
                            selectedCategory?.let { category ->
                                onSaveRule(selectedTransactions.toList(), category)
                                onNavigateBack()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectTransactionsStep(
    transactions: List<CategorizedTransaction>,
    selectedTransactions: List<CategorizedTransaction>,
    onToggleTransaction: (CategorizedTransaction) -> Unit,
    onContinue: () -> Unit,
) {
    Column {
        Text(
            text = "Select Similar Transactions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Text(
            text = "Select 2 or more transactions with the same merchant or pattern:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Text(
            text = "Selected: ${selectedTransactions.size}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(transactions) { transaction ->
                TransactionSelectionCard(
                    transaction = transaction,
                    isSelected = selectedTransactions.contains(transaction),
                    onToggle = { onToggleTransaction(transaction) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onContinue,
            enabled = selectedTransactions.size >= 2,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun TransactionSelectionCard(
    transaction: CategorizedTransaction,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            ) {
                Text(
                    text = transaction.transaction.merchant ?: "Unknown",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = "$${transaction.transaction.amount.toInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "Currently: ${transaction.category.name.replace('_', ' ')}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SelectCategoryStep(
    selectedCategory: Category?,
    onSelectCategory: (Category) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column {
        Text(
            text = "Select Correct Category",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Text(
            text = "What category should these transactions be?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(Category.entries.filter { it != Category.UNCATEGORIZED }) { category ->
                Card(
                    onClick = { onSelectCategory(category) },
                    colors = if (selectedCategory == category) {
                        androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                    } else {
                        androidx.compose.material3.CardDefaults.cardColors()
                    },
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
            ) {
                Text("Back")
            }

            Button(
                onClick = onConfirm,
                enabled = selectedCategory != null,
                modifier = Modifier.weight(1f),
            ) {
                Text("Review")
            }
        }
    }
}

@Composable
private fun ReviewRuleStep(
    selectedTransactions: List<CategorizedTransaction>,
    selectedCategory: Category?,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Column {
        Text(
            text = "Review Rule",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Rule Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                Text(
                    text = "Transactions: ${selectedTransactions.size}",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Text(
                    text = "Category: ${selectedCategory?.name?.replace('_', ' ') ?: "None"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Text(
            text = "Selected Transactions:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(selectedTransactions) { transaction ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = transaction.transaction.merchant ?: "Unknown",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "$${transaction.transaction.amount.toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
            ) {
                Text("Back")
            }

            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
            ) {
                Text("Save Rule")
            }
        }
    }
}

private enum class CategoryTeachingStep {
    SELECT_TRANSACTIONS,
    SELECT_CATEGORY,
    REVIEW_RULE,
}
