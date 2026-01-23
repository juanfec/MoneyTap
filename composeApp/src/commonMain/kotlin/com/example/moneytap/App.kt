package com.example.moneytap

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.moneytap.navigation.Route
import com.example.moneytap.permission.rememberSmsPermissionState
import com.example.moneytap.presentation.viewmodel.SmsViewModel
import com.example.moneytap.presentation.viewmodel.SpendingViewModel
import com.example.moneytap.ui.screen.SmsInboxScreen
import com.example.moneytap.ui.screen.SpendingSummaryScreen
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
                )
            }

            composable<Route.SpendingSummary> {
                val viewModel: SpendingViewModel = koinViewModel()
                val permissionState = rememberSmsPermissionState { granted, shouldShowRationale ->
                    viewModel.onPermissionResult(granted, shouldShowRationale)
                }

                SpendingSummaryScreen(
                    viewModel = viewModel,
                    onRequestPermission = { permissionState.launchPermissionRequest() },
                    onOpenSettings = { permissionState.openSettings() },
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
    }
}