package com.example.moneytap.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.moneytap.domain.model.PermissionState

@Composable
fun PermissionRequestCard(
    permissionState: PermissionState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "SMS Permission Required",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when (permissionState) {
                    PermissionState.PermanentlyDenied ->
                        "SMS permission was permanently denied. Please enable it in Settings to view your messages."
                    else ->
                        "This app needs permission to read your SMS messages to display them here."
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            when (permissionState) {
                PermissionState.PermanentlyDenied -> {
                    Button(onClick = onOpenSettings) {
                        Text("Open Settings")
                    }
                }
                else -> {
                    Button(onClick = onRequestPermission) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}
