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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moneytap.domain.model.CategorizedTransaction
import com.example.moneytap.domain.model.Category
import com.example.moneytap.domain.model.TransactionType
import com.example.moneytap.presentation.viewmodel.SpendingViewModel
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
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val category = Category.entries.find { it.name == categoryName }
    val transaction = uiState.summary?.byCategory
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
    modifier: Modifier = Modifier,
) {
    val txInfo = transaction.transaction

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
