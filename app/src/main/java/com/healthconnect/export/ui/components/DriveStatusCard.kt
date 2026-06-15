package com.healthconnect.export.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.healthconnect.export.R
import com.healthconnect.export.viewmodel.DriveStatus

@Composable
fun DriveStatusCard(
    status: DriveStatus,
    onSync: () -> Unit,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    val (icon, title, color) = when (status) {
        is DriveStatus.NotConnected ->
            Triple(Icons.Default.CloudOff, stringResource(R.string.drive_not_connected), MaterialTheme.colorScheme.error)
        is DriveStatus.Connected ->
            Triple(Icons.Default.Cloud, stringResource(R.string.drive_connected), MaterialTheme.colorScheme.primary)
        is DriveStatus.Syncing ->
            Triple(Icons.Default.Refresh, stringResource(R.string.drive_syncing), MaterialTheme.colorScheme.tertiary)
        is DriveStatus.Synced ->
            Triple(Icons.Default.Cloud, stringResource(R.string.drive_synced, status.filesCount), MaterialTheme.colorScheme.primary)
        is DriveStatus.Error ->
            Triple(Icons.Default.CloudOff, stringResource(R.string.drive_error, status.error), MaterialTheme.colorScheme.error)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                title,
                modifier = Modifier.weight(1f),
                color = color
            )
            when (status) {
                is DriveStatus.NotConnected, is DriveStatus.Error -> {
                    TextButton(onClick = onSignInClick) {
                        Text(stringResource(R.string.sign_in))
                    }
                }
                is DriveStatus.Connected, is DriveStatus.Synced -> {
                    TextButton(onClick = onSync) {
                        Text(stringResource(R.string.sync_now))
                    }
                    TextButton(onClick = onSignOutClick) {
                        Text(stringResource(R.string.sign_out))
                    }
                }
                is DriveStatus.Syncing -> {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}
