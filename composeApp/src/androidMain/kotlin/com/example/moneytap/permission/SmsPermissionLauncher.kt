package com.example.moneytap.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat

@Composable
actual fun rememberSmsPermissionState(
    onPermissionResult: (granted: Boolean, shouldShowRationale: Boolean) -> Unit,
): SmsPermissionState {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val activity = context as? androidx.activity.ComponentActivity
        val shouldShowRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(
                it,
                Manifest.permission.READ_SMS,
            )
        } ?: false
        onPermissionResult(granted, shouldShowRationale)
    }

    return remember(launcher, context) {
        SmsPermissionState(
            launchPermissionRequest = {
                launcher.launch(Manifest.permission.READ_SMS)
            },
            openSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
        )
    }
}
