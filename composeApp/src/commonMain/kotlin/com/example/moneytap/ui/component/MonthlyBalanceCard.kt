package com.example.moneytap.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.moneytap.domain.model.MonthlySpendingSummary
import kotlin.math.roundToInt

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
                color = if (summary.balance >= 0) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }.copy(alpha = 0.8f),
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
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 32.dp),
                color = if (summary.balance >= 0) {
                    MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.3f)
                },
            )
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
                        color = if (summary.balance >= 0) {
                            MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        },
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
                        color = if (summary.balance >= 0) {
                            MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        },
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
                color = if (summary.balance >= 0) {
                    MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                },
            )
        }
    }
}

private fun formatCurrency(amount: Double): String {
    val rounded = amount.roundToInt()
    val formatted = rounded.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$$formatted COP"
}
