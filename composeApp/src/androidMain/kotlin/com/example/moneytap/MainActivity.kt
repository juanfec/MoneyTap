package com.example.moneytap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.moneytap.permission.rememberSmsPermissionState
import com.example.moneytap.presentation.viewmodel.SmsViewModel
import com.example.moneytap.ui.screen.SmsInboxScreen
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
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
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}