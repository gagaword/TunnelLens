package com.gagaworld.modernsocks.ui.home

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import java.util.Locale
import com.gagaworld.modernsocks.R
import com.gagaworld.modernsocks.ui.theme.ModernSocksTheme
import com.gagaworld.modernsocks.vpn.ConnectionFailure
import com.gagaworld.modernsocks.vpn.ConnectionState
import com.gagaworld.modernsocks.vpn.VpnController
import com.gagaworld.modernsocks.data.repository.ProfileRepository

const val CONNECTION_STATUS_TEST_TAG = "connection_status"

@Composable
fun HomeRoute(
    vpnController: VpnController,
    profileRepository: ProfileRepository,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel {
        HomeViewModel(vpnController, profileRepository)
    },
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionRequest by viewModel.permissionRequest.collectAsStateWithLifecycle()
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onVpnPermissionResult(result.resultCode == Activity.RESULT_OK)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Notification permission affects visibility, not whether an FGS may start.
        viewModel.onPrimaryAction()
    }

    LaunchedEffect(permissionRequest?.generation) {
        permissionRequest?.let { vpnPermissionLauncher.launch(it.intent) }
    }

    HomeScreen(
        uiState = uiState,
        modifier = modifier,
        onPrimaryAction = {
            val isStarting = uiState.connectionState is ConnectionState.Disconnected ||
                uiState.connectionState is ConnectionState.Error
            val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            if (isStarting && needsNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onPrimaryAction()
            }
        },
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusText = stringResource(statusTextRes(uiState.connectionState))
    val statusDescription = stringResource(R.string.connection_status_description, statusText)
    val action = primaryAction(uiState.connectionState)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        Surface(
            modifier = Modifier
                .size(196.dp)
                .semantics {
                    contentDescription = statusDescription
                },
            shape = CircleShape,
            color = statusContainerColor(uiState.connectionState),
            contentColor = statusContentColor(uiState.connectionState),
            tonalElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.connection_status_label),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(statusContentColor(uiState.connectionState)),
                    )
                    Text(
                        text = statusText,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .testTag(CONNECTION_STATUS_TEST_TAG),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.current_profile_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = when {
                            uiState.isLoadingProfile -> stringResource(R.string.profile_loading)
                            uiState.currentProfileName != null -> uiState.currentProfileName
                            else -> stringResource(R.string.no_profile_selected)
                        },
                        modifier = Modifier.padding(top = 5.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = uiState.currentProfileAddress
                            ?: stringResource(R.string.profile_placeholder_address),
                        modifier = Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (uiState.connectionState is ConnectionState.Connected ||
            uiState.connectionState is ConnectionState.Reconnecting
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetricItem(
                        label = stringResource(R.string.home_metric_duration),
                        value = formatDuration(uiState.metrics.connectedDurationMillis),
                    )
                    MetricItem(
                        label = stringResource(R.string.home_metric_upload),
                        value = formatBytes(uiState.metrics.uploadedBytes),
                    )
                    MetricItem(
                        label = stringResource(R.string.home_metric_download),
                        value = formatBytes(uiState.metrics.downloadedBytes),
                    )
                }
                if (uiState.connectionState is ConnectionState.Reconnecting) {
                    Text(
                        text = stringResource(
                            R.string.home_reconnect_attempt,
                            uiState.metrics.reconnectAttempt,
                        ),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        val error = uiState.connectionState as? ConnectionState.Error
        if (error != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(
                    text = stringResource(failureTextRes(error.reason)),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Button(
            onClick = onPrimaryAction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .height(56.dp),
            enabled = action.enabled && uiState.canConnect,
        ) {
            Text(
                stringResource(
                    if (!uiState.isLoadingProfile && !uiState.canConnect) {
                        R.string.action_add_profile_first
                    } else {
                        action.labelRes
                    },
                ),
            )
        }
        Text(
            text = stringResource(R.string.fake_connection_notice),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Text(
            text = value,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0) / 1_000)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0)
    return when {
        safe >= 1024L * 1024L * 1024L -> String.format(
            Locale.getDefault(),
            "%.1f GB",
            safe / (1024.0 * 1024.0 * 1024.0),
        )
        safe >= 1024L * 1024L -> String.format(
            Locale.getDefault(),
            "%.1f MB",
            safe / (1024.0 * 1024.0),
        )
        safe >= 1024L -> String.format(Locale.getDefault(), "%.1f KB", safe / 1024.0)
        else -> "$safe B"
    }
}

private data class PrimaryAction(
    @param:StringRes val labelRes: Int,
    val enabled: Boolean,
)

private fun primaryAction(connectionState: ConnectionState): PrimaryAction = when (connectionState) {
    ConnectionState.Disconnected,
    is ConnectionState.Error,
    -> PrimaryAction(R.string.action_connect_fake, enabled = true)

    ConnectionState.Connected,
    ConnectionState.Reconnecting,
    -> PrimaryAction(R.string.action_disconnect_fake, enabled = true)
    ConnectionState.PreparingPermission,
    ConnectionState.Starting,
    ConnectionState.Stopping,
    -> PrimaryAction(R.string.action_in_progress, enabled = false)
}

@StringRes
private fun statusTextRes(connectionState: ConnectionState): Int = when (connectionState) {
    ConnectionState.Disconnected -> R.string.connection_disconnected
    ConnectionState.PreparingPermission -> R.string.connection_preparing_permission
    ConnectionState.Starting -> R.string.connection_starting
    ConnectionState.Connected -> R.string.connection_connected
    ConnectionState.Reconnecting -> R.string.connection_reconnecting
    ConnectionState.Stopping -> R.string.connection_stopping
    is ConnectionState.Error -> R.string.connection_error
}

@StringRes
private fun failureTextRes(failure: ConnectionFailure): Int = when (failure) {
    ConnectionFailure.PERMISSION_DENIED -> R.string.connection_error_permission_denied
    ConnectionFailure.NO_PROFILE -> R.string.connection_error_no_profile
    ConnectionFailure.SERVICE_START_FAILED -> R.string.connection_error_service_start
    ConnectionFailure.SERVICE_STOP_FAILED -> R.string.connection_error_service_stop
    ConnectionFailure.TUN_ESTABLISH_FAILED -> R.string.connection_error_tun
    ConnectionFailure.NATIVE_START_FAILED -> R.string.connection_error_native_start
    ConnectionFailure.NATIVE_EXITED -> R.string.connection_error_native_exit
    ConnectionFailure.NATIVE_STOP_TIMEOUT -> R.string.connection_error_native_stop_timeout
    ConnectionFailure.NETWORK_UNAVAILABLE -> R.string.connection_error_network_unavailable
    ConnectionFailure.PROXY_UNREACHABLE -> R.string.connection_error_proxy_unreachable
    ConnectionFailure.AUTHENTICATION_FAILED -> R.string.connection_error_authentication
    ConnectionFailure.SOCKS_PROTOCOL_ERROR -> R.string.connection_error_socks_protocol
    ConnectionFailure.RECONNECT_EXHAUSTED -> R.string.connection_error_reconnect_exhausted
    ConnectionFailure.APP_ROUTING_INVALID -> R.string.connection_error_app_routing
    ConnectionFailure.PERMISSION_REVOKED -> R.string.connection_error_revoked
    ConnectionFailure.UNEXPECTED -> R.string.connection_error_unexpected
}

@Composable
private fun statusContainerColor(connectionState: ConnectionState) = when (connectionState) {
    ConnectionState.Connected -> MaterialTheme.colorScheme.secondaryContainer
    is ConnectionState.Error -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
private fun statusContentColor(connectionState: ConnectionState) = when (connectionState) {
    ConnectionState.Connected -> MaterialTheme.colorScheme.onSecondaryContainer
    is ConnectionState.Error -> MaterialTheme.colorScheme.onErrorContainer
    else -> MaterialTheme.colorScheme.onPrimaryContainer
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeDisconnectedPreview() {
    ModernSocksTheme(dynamicColor = false) {
        HomeScreen(
            uiState = HomeUiState(),
            onPrimaryAction = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 720, heightDp = 720)
@Composable
private fun HomeConnectedDarkPreview() {
    ModernSocksTheme(darkTheme = true, dynamicColor = false) {
        HomeScreen(
            uiState = HomeUiState(
                connectionState = ConnectionState.Connected,
                isLoadingProfile = false,
                currentProfileName = stringResource(R.string.preview_profile_name),
                currentProfileAddress = stringResource(R.string.preview_profile_address),
                canConnect = true,
            ),
            onPrimaryAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeErrorPreview() {
    ModernSocksTheme(dynamicColor = false) {
        HomeScreen(
            uiState = HomeUiState(
                connectionState = ConnectionState.Error(ConnectionFailure.UNEXPECTED),
            ),
            onPrimaryAction = {},
        )
    }
}
