package com.example.moneytap.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moneytap.domain.model.PermissionState
import com.example.moneytap.domain.model.SpendingSummary
import com.example.moneytap.presentation.state.SpendingError
import com.example.moneytap.presentation.state.SpendingUiState
import com.example.moneytap.presentation.viewmodel.SpendingEvent
import com.example.moneytap.presentation.viewmodel.SpendingViewModel
import com.example.moneytap.ui.component.CategorySpendingCard
import com.example.moneytap.ui.component.MonthSelector
import com.example.moneytap.ui.component.MonthlyBalanceCard
import com.example.moneytap.ui.component.PermissionRequestCard
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingSummaryScreen(
    viewModel: SpendingViewModel,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    onCategoryClick: (categoryName: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                SpendingEvent.RequestPermission -> onRequestPermission()
                SpendingEvent.OpenSettings -> onOpenSettings()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spending Summary") },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        SpendingSummaryContent(
            uiState = uiState,
            onRequestPermission = { viewModel.checkPermission() },
            onOpenSettings = onOpenSettings,
            onRefresh = { viewModel.refresh() },
            onCategoryClick = onCategoryClick,
            onPreviousMonth = { viewModel.previousMonth() },
            onNextMonth = { viewModel.nextMonth() },
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
private fun SpendingSummaryContent(
    uiState: SpendingUiState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onCategoryClick: (categoryName: String) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            !uiState.isPlatformSupported -> {
                PlatformNotSupportedContent(
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            uiState.permissionState != PermissionState.Granted &&
                uiState.permissionState != PermissionState.NotApplicable -> {
                PermissionRequestCard(
                    permissionState = uiState.permissionState,
                    onRequestPermission = onRequestPermission,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                )
            }
            uiState.error != null && uiState.summary == null -> {
                ErrorContent(
                    error = uiState.error,
                    onRetry = onRefresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            uiState.monthlySummary != null && uiState.monthlySummary.transactionCount > 0 -> {
                SpendingListContent(
                    uiState = uiState,
                    onRefresh = onRefresh,
                    onCategoryClick = onCategoryClick,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                )
            }
            else -> {
                NoTransactionsContent(
                    onRefresh = onRefresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

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
                    canGoBack = run {
                        val available = uiState.availableMonths
                        val currentIndex = available.indexOf(month)
                        currentIndex < available.lastIndex
                    },
                    canGoForward = run {
                        val available = uiState.availableMonths
                        val currentIndex = available.indexOf(month)
                        currentIndex > 0
                    },
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                )
            }
        }

        // Monthly balance card
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

@Composable
private fun TotalSpendingCard(
    summary: SpendingSummary,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Total Spending",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatCurrency(summary.totalSpending),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${summary.transactionCount} transactions in ${summary.byCategory.size} categories",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun PlatformNotSupportedContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "SMS Not Supported",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Spending tracking requires SMS access, which is only available on Android.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorContent(
    error: SpendingError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when (error) {
                is SpendingError.PermissionDenied -> "Permission Denied"
                is SpendingError.PermissionPermanentlyDenied -> "Permission Required"
                is SpendingError.PlatformNotSupported -> "Not Supported"
                is SpendingError.NoTransactions -> "No Transactions"
                is SpendingError.Generic -> "Error"
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (error) {
                is SpendingError.NoTransactions -> "No spending transactions found in your SMS inbox."
                is SpendingError.Generic -> error.message
                else -> "An error occurred while loading spending data."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun NoTransactionsContent(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No Transactions",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No spending transactions found. Make sure you have bank SMS messages in your inbox.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRefresh) {
            Text("Refresh")
        }
    }
}

private fun formatCurrency(amount: Double): String {
    val rounded = amount.roundToInt()
    val formatted = rounded.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$$formatted COP"
}
