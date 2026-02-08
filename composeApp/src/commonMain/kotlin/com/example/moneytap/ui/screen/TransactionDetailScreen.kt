package com.example.moneytap.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.TransactionType
import com.example.moneytap.presentation.viewmodel.SpendingViewModel
import com.example.moneytap.ui.component.CategoryPickerBottomSheet
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

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

    val category = Category.entries.find { it.name == categoryName }
    val transaction = uiState.monthlySummary?.byCategory
        ?.get(category)
        ?.transactions
        ?.getOrNull(transactionIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        if (transaction != null) {
            TransactionDetailContent(
                transaction = transaction,
                onTeachPattern = onTeachPattern,
                onChangeCategory = { smsId, newCategory ->
                    viewModel.changeTransactionCategory(smsId, newCategory)
                },
                onChangeType = { smsId, newType ->
                    viewModel.changeTransactionType(smsId, newType)
                },
                modifier = Modifier.padding(paddingValues),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Transaction not found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TransactionDetailContent(
    transaction: CategorizedTransaction,
    onTeachPattern: (Long) -> Unit,
    onChangeCategory: (Long, Category) -> Unit,
    onChangeType: (Long, TransactionType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val txInfo = transaction.transaction
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showTypePicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "amount_card") {
            AmountCard(
                amount = txInfo.amount,
                type = txInfo.type,
            )
        }

        item(key = "details_card") {
            DetailsCard(transaction = transaction)
        }

        item(key = "raw_message_card") {
            RawMessageCard(rawMessage = txInfo.rawMessage)
        }

        // User corrected badge
        if (transaction.userCorrected) {
            item(key = "user_corrected_badge") {
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
        }

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

        item(key = "change_type_button") {
            OutlinedButton(
                onClick = { showTypePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text("Change Transaction Type")
            }
        }

        item(key = "teach_pattern_button") {
            OutlinedButton(
                onClick = { onTeachPattern(txInfo.smsId) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text("Teach Pattern for This SMS")
            }
        }
    }

    // Category picker bottom sheet
    if (showCategoryPicker) {
        CategoryPickerBottomSheet(
            currentCategory = transaction.category,
            onCategorySelected = { newCategory ->
                onChangeCategory(txInfo.smsId, newCategory)
            },
            onDismiss = { showCategoryPicker = false },
        )
    }

    // Transaction type picker bottom sheet
    if (showTypePicker) {
        TransactionTypePickerBottomSheet(
            currentType = txInfo.type,
            onTypeSelected = { newType ->
                onChangeType(txInfo.smsId, newType)
                showTypePicker = false
            },
            onDismiss = { showTypePicker = false },
        )
    }
}

@Composable
private fun AmountCard(
    amount: Double,
    type: TransactionType,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (type) {
                TransactionType.CREDIT -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = type.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = when (type) {
                    TransactionType.CREDIT -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onErrorContainer
                }.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatCurrency(amount),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = when (type) {
                    TransactionType.CREDIT -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onErrorContainer
                },
            )
        }
    }
}

@Composable
private fun DetailsCard(
    transaction: CategorizedTransaction,
    modifier: Modifier = Modifier,
) {
    val txInfo = transaction.transaction

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))

            txInfo.merchant?.let { merchant ->
                DetailRow(label = "Merchant", value = merchant)
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }

            DetailRow(label = "Bank", value = txInfo.bankName)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            DetailRow(label = "Category", value = transaction.category.displayName)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            DetailRow(
                label = "Match Type",
                value = transaction.matchType.name.lowercase()
                    .replaceFirstChar { it.uppercase() },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            DetailRow(
                label = "Confidence",
                value = "${(transaction.confidence * 100).toInt()}%",
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            DetailRow(label = "Date & Time", value = formatFullDateTime(txInfo.timestamp))

            txInfo.cardLast4?.let { cardLast4 ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                DetailRow(label = "Card", value = "**** **** **** $cardLast4")
            }

            txInfo.balance?.let { balance ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                DetailRow(label = "Balance After", value = formatCurrency(balance))
            }

            txInfo.reference?.let { reference ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                DetailRow(label = "Reference", value = reference)
            }

            txInfo.description?.let { description ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                DetailRow(label = "Description", value = description)
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RawMessageCard(
    rawMessage: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = "Original SMS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = rawMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val TransactionType.displayName: String
    get() = when (this) {
        TransactionType.DEBIT -> "Purchase"
        TransactionType.CREDIT -> "Income"
        TransactionType.TRANSFER -> "Transfer"
        TransactionType.WITHDRAWAL -> "Withdrawal"
    }

private fun formatCurrency(amount: Double): String {
    val rounded = amount.roundToInt()
    val formatted = rounded.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$$formatted COP"
}

private fun formatFullDateTime(instant: kotlinx.datetime.Instant): String {
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = localDateTime.month.name.take(3).lowercase()
        .replaceFirstChar { it.uppercase() }
    return "${localDateTime.dayOfMonth} $month ${localDateTime.year}, " +
        "${localDateTime.hour.toString().padStart(2, '0')}:" +
        "${localDateTime.minute.toString().padStart(2, '0')}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionTypePickerBottomSheet(
    currentType: TransactionType,
    onTypeSelected: (TransactionType) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Select Transaction Type",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            TransactionType.entries.forEach { type ->
                Card(
                    onClick = { onTypeSelected(type) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = if (type == currentType) {
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                    } else {
                        CardDefaults.cardColors()
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = type.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (type == currentType) {
                            Text(
                                text = "Current",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
