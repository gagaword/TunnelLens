package com.gagaworld.modernsocks.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gagaworld.modernsocks.R
import com.gagaworld.modernsocks.data.model.ProxyProfile
import com.gagaworld.modernsocks.data.model.endpoint
import com.gagaworld.modernsocks.data.repository.ProfileRepository
import com.gagaworld.modernsocks.data.transfer.ProfileTransferManager
import androidx.compose.material3.TextButton
import com.gagaworld.modernsocks.ui.theme.ModernSocksTheme

@Composable
fun ProfilesRoute(
    repository: ProfileRepository,
    transferManager: ProfileTransferManager,
    onAddProfile: () -> Unit,
    onEditProfile: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfilesViewModel = viewModel { ProfilesViewModel(repository, transferManager) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importFrom) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportTo) }
    val exportFileName = stringResource(R.string.profile_export_file_name)
    ProfilesScreen(
        uiState = uiState,
        modifier = modifier,
        onAddProfile = onAddProfile,
        onEditProfile = onEditProfile,
        onImportProfiles = { importLauncher.launch(arrayOf("application/json", "text/json")) },
        onExportProfiles = { exportLauncher.launch(exportFileName) },
        onSelectProfile = viewModel::select,
        onDuplicateProfile = viewModel::duplicate,
        onDeleteProfile = viewModel::delete,
        onUndoDelete = viewModel::undoDelete,
        onDeleteMessageConsumed = viewModel::consumeDeleteMessage,
        onErrorConsumed = viewModel::consumeError,
        onTransferResultConsumed = viewModel::consumeTransferResult,
    )
}

@Composable
fun ProfilesScreen(
    uiState: ProfilesUiState,
    onAddProfile: () -> Unit,
    onEditProfile: (Long) -> Unit,
    onImportProfiles: () -> Unit,
    onExportProfiles: () -> Unit,
    onSelectProfile: (Long) -> Unit,
    onDuplicateProfile: (Long, String) -> Unit,
    onDeleteProfile: (Long) -> Unit,
    onUndoDelete: () -> Unit,
    onDeleteMessageConsumed: () -> Unit,
    onErrorConsumed: () -> Unit,
    onTransferResultConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val undo = uiState.undoDelete
    val undoLabel = stringResource(R.string.action_undo)
    val deletedMessage = undo?.let {
        stringResource(R.string.profile_deleted, it.profileName)
    }
    val failureMessage = stringResource(R.string.profile_operation_failed)
    val transferResult = uiState.transferResult
    val transferMessage = transferResult?.let { result ->
        pluralStringResource(
            if (result.type == ProfileTransferType.IMPORTED) R.plurals.profiles_imported
            else R.plurals.profiles_exported,
            result.count,
            result.count,
        )
    }

    LaunchedEffect(undo?.token) {
        if (undo != null && deletedMessage != null) {
            val result = snackbarHostState.showSnackbar(
                message = deletedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) onUndoDelete()
            else onDeleteMessageConsumed()
        }
    }
    LaunchedEffect(uiState.operationFailed) {
        if (uiState.operationFailed) {
            snackbarHostState.showSnackbar(failureMessage)
            onErrorConsumed()
        }
    }
    LaunchedEffect(transferResult?.token) {
        if (transferMessage != null) {
            snackbarHostState.showSnackbar(transferMessage)
            onTransferResultConsumed()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val description = stringResource(R.string.action_add_profile)
            FloatingActionButton(
                onClick = onAddProfile,
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                Text(text = "+", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            uiState.profiles.isEmpty() -> EmptyProfiles(
                modifier = Modifier.padding(padding),
                onImportProfiles = onImportProfiles,
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        Text(
                            text = stringResource(R.string.profiles_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row {
                            TextButton(onClick = onImportProfiles) {
                                Text(stringResource(R.string.action_import_profiles))
                            }
                            TextButton(onClick = onExportProfiles) {
                                Text(stringResource(R.string.action_export_profiles))
                            }
                        }
                    }
                }
                items(uiState.profiles, key = ProxyProfile::id) { profile ->
                    val copyName = stringResource(R.string.profile_copy_name, profile.name)
                    ProfileCard(
                        profile = profile,
                        onSelect = { onSelectProfile(profile.id) },
                        onEdit = { onEditProfile(profile.id) },
                        onCopy = { onDuplicateProfile(profile.id, copyName) },
                        onDelete = { onDeleteProfile(profile.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyProfiles(
    onImportProfiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.padding(24.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.profiles_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(
                    onClick = onImportProfiles,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.action_import_profiles))
                }
                Text(
                    text = stringResource(R.string.profiles_empty_body),
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: ProxyProfile,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember(profile.id) { mutableStateOf(false) }
    val selectDescription = stringResource(R.string.action_select_profile, profile.name)
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (profile.isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = profile.isSelected,
                onClick = onSelect,
                modifier = Modifier.semantics { contentDescription = selectDescription },
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (profile.isSelected) {
                        Text(
                            text = stringResource(R.string.profile_selected),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Text(
                    text = profile.endpoint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        if (profile.authenticationEnabled) {
                            R.string.profile_authentication_enabled
                        } else {
                            R.string.profile_no_authentication
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(
                            R.string.action_more_for_profile,
                            profile.name,
                        ),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        onClick = { menuExpanded = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_copy)) },
                        onClick = { menuExpanded = false; onCopy() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfilesScreenPreview() {
    ModernSocksTheme(dynamicColor = false) {
        ProfilesScreen(
            uiState = ProfilesUiState(
                isLoading = false,
                profiles = listOf(
                    ProxyProfile(
                        id = 1,
                        name = stringResource(R.string.preview_profile_name),
                        host = "example.invalid",
                        port = 1080,
                        authenticationEnabled = true,
                        hasStoredCredentials = true,
                        dnsServer = null,
                        mtu = 1500,
                        bypassLan = true,
                        autoReconnect = true,
                        appRoutingMode = com.gagaworld.modernsocks.data.model.AppRoutingMode.ALL_APPS,
                        appRoutingPackageCount = 0,
                        isSelected = true,
                        createdAt = 0,
                        updatedAt = 0,
                    ),
                ),
            ),
            onAddProfile = {},
            onEditProfile = {},
            onImportProfiles = {},
            onExportProfiles = {},
            onSelectProfile = {},
            onDuplicateProfile = { _, _ -> },
            onDeleteProfile = {},
            onUndoDelete = {},
            onDeleteMessageConsumed = {},
            onErrorConsumed = {},
            onTransferResultConsumed = {},
        )
    }
}
