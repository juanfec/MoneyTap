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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.moneytap.domain.model.PermissionState
import com.example.moneytap.presentation.state.SmsUiError
import com.example.moneytap.presentation.state.SmsUiState
import com.example.moneytap.presentation.viewmodel.SmsEvent
import com.example.moneytap.presentation.viewmodel.SmsViewModel
import com.example.moneytap.ui.component.PermissionRequestCard
import com.example.moneytap.ui.component.SmsMessageCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsInboxScreen(
    viewModel: SmsViewModel,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                SmsEvent.RequestPermission -> onRequestPermission()
                SmsEvent.OpenSettings -> onOpenSettings()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermission()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SMS Inbox") },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        SmsInboxContent(
            uiState = uiState,
            onRequestPermission = { viewModel.checkPermission() },
            onOpenSettings = onOpenSettings,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
private fun SmsInboxContent(
    uiState: SmsUiState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
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
            uiState.error != null && uiState.messages.isEmpty() -> {
                ErrorContent(
                    error = uiState.error,
                    onRetry = onRefresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            uiState.messages.isEmpty() -> {
                EmptyInboxContent(
                    onRefresh = onRefresh,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = uiState.messages,
                        key = { it.id },
                    ) { message ->
                        SmsMessageCard(
                            message = message,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
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
            text = "SMS reading is only available on Android devices.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorContent(
    error: SmsUiError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when (error) {
                is SmsUiError.PermissionDenied -> "Permission Denied"
                is SmsUiError.PermissionPermanentlyDenied -> "Permission Required"
                is SmsUiError.PlatformNotSupported -> "Not Supported"
                is SmsUiError.Generic -> "Error"
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (error) {
                is SmsUiError.Generic -> error.message
                else -> "An error occurred while loading messages."
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
private fun EmptyInboxContent(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No Messages",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your SMS inbox is empty.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRefresh) {
            Text("Refresh")
        }
    }
}
