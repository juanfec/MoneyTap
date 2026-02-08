package com.example.moneytap

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.moneytap.navigation.Route
import com.example.moneytap.permission.rememberSmsPermissionState
import com.example.moneytap.presentation.viewmodel.SmsViewModel
import com.example.moneytap.presentation.viewmodel.SpendingViewModel
import com.example.moneytap.presentation.viewmodel.TeachingViewModel
import com.example.moneytap.ui.screen.CategoryTeachingScreen
import com.example.moneytap.ui.screen.CategoryTransactionsScreen
import com.example.moneytap.ui.screen.SmsInboxScreen
import com.example.moneytap.ui.screen.SpendingSummaryScreen
import com.example.moneytap.ui.screen.TeachingScreen
import com.example.moneytap.ui.screen.TransactionDetailScreen
import org.koin.compose.viewmodel.koinViewModel

/**
 * Main application entry point composable.
 * This is the shared entry point used by all platforms (Android, iOS, Desktop).
 */
@Composable
fun App() {
    MaterialTheme {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = Route.SpendingSummary,
        ) {
            composable<Route.SmsInbox> {
                val viewModel: SmsViewModel = koinViewModel()
                val permissionState = rememberSmsPermissionState { granted, shouldShowRationale ->
                    viewModel.onPermissionResult(granted, shouldShowRationale)
                }

                SmsInboxScreen(
                    viewModel = viewModel,
                    onRequestPermission = { permissionState.launchPermissionRequest() },
                    onOpenSettings = { permissionState.openSettings() },
                    onTeachPattern = { sms ->
                        navController.navigate(Route.Teaching(smsId = sms.id))
                    },
                )
            }

            composable<Route.SpendingSummary> {
                val viewModel: SpendingViewModel = koinViewModel(
                    key = "spending_summary_vm"
                )
                val permissionState = rememberSmsPermissionState { granted, shouldShowRationale ->
                    viewModel.onPermissionResult(granted, shouldShowRationale)
                }

                SpendingSummaryScreen(
                    viewModel = viewModel,
                    onRequestPermission = { permissionState.launchPermissionRequest() },
                    onOpenSettings = { permissionState.openSettings() },
                    onNavigateBack = { navController.popBackStack() },
                    onCategoryClick = { categoryName ->
                        navController.navigate(Route.CategoryTransactions(categoryName))
                    },
                )
            }

            composable<Route.CategoryTransactions> {
                val route = it.toRoute<Route.CategoryTransactions>()
                val viewModel: SpendingViewModel = koinViewModel(
                    key = "spending_summary_vm"
                )
                
                CategoryTransactionsScreen(
                    categoryName = route.categoryName,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onTransactionClick = { transactionIndex ->
                        navController.navigate(
                            Route.TransactionDetail(
                                categoryName = route.categoryName,
                                transactionIndex = transactionIndex,
                            )
                        )
                    },
                )
            }

            composable<Route.TransactionDetail> {
                val route = it.toRoute<Route.TransactionDetail>()
                val viewModel: SpendingViewModel = koinViewModel(
                    key = "spending_summary_vm"
                )

                TransactionDetailScreen(
                    categoryName = route.categoryName,
                    transactionIndex = route.transactionIndex,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onTeachPattern = { smsId ->
                        navController.navigate(Route.Teaching(smsId = smsId))
                    },
                )
            }

            composable<Route.Teaching> {
                val route = it.toRoute<Route.Teaching>()
                val viewModel: TeachingViewModel = koinViewModel()
                val smsViewModel: SmsViewModel = koinViewModel()

                // Find the SMS from the list
                val uiState by smsViewModel.uiState.collectAsStateWithLifecycle()
                val sms = uiState.messages.find { msg -> msg.id == route.smsId }

                TeachingScreen(
                    viewModel = viewModel,
                    initialSms = sms,
                    onNavigateBack = { navController.popBackStack() },
                )
            }

            composable<Route.CategoryTeaching> {
                val spendingViewModel: SpendingViewModel = koinViewModel(
                    key = "spending_summary_vm"
                )

                // Get all transactions from spending summary
                val transactions = spendingViewModel.uiState.value.summary?.byCategory
                    ?.values
                    ?.flatMap { it.transactions }
                    ?: emptyList()

                CategoryTeachingScreen(
                    transactions = transactions,
                    onSaveRule = { selectedTransactions, category ->
                        // TODO: Implement rule saving logic with CategoryTeachingEngine
                    },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}