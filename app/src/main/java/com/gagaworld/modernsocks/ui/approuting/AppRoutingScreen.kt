package com.gagaworld.modernsocks.ui.approuting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gagaworld.modernsocks.R
import com.gagaworld.modernsocks.data.apps.InstalledApp
import com.gagaworld.modernsocks.data.apps.InstalledAppRepository
import com.gagaworld.modernsocks.data.model.AppRoutingMode
import com.gagaworld.modernsocks.data.repository.ProfileRepository
import com.gagaworld.modernsocks.ui.theme.ModernSocksTheme

@Composable
fun AppRoutingRoute(
    profileId: Long,
    profileRepository: ProfileRepository,
    installedAppRepository: InstalledAppRepository,
    onBack: () -> Unit,
    viewModel: AppRoutingViewModel = viewModel(key = "app-routing-$profileId") {
        AppRoutingViewModel(profileId, profileRepository, installedAppRepository)
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saveCompleted) {
        if (state.saveCompleted) onBack()
    }
    AppRoutingScreen(state, viewModel::onAction, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoutingScreen(
    state: AppRoutingUiState,
    onAction: (AppRoutingAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_routing_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isSaving) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.app_routing_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RoutingModeChip(AppRoutingMode.ALL_APPS, state, onAction)
                RoutingModeChip(AppRoutingMode.ONLY_SELECTED, state, onAction)
                RoutingModeChip(AppRoutingMode.BYPASS_SELECTED, state, onAction)
            }
            if (state.mode != AppRoutingMode.ALL_APPS) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onAction(AppRoutingAction.QueryChanged(it)) },
                    label = { Text(stringResource(R.string.app_routing_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_routing_show_system))
                        Text(
                            pluralStringResource(
                                R.plurals.app_routing_selected_count,
                                state.selectedPackages.size,
                                state.selectedPackages.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = state.showSystemApps,
                        onCheckedChange = { onAction(AppRoutingAction.ShowSystemChanged(it)) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onAction(AppRoutingAction.SelectVisible) }) {
                        Text(stringResource(R.string.action_select_visible))
                    }
                    TextButton(onClick = { onAction(AppRoutingAction.Clear) }) {
                        Text(stringResource(R.string.action_clear_selection))
                    }
                }
                if (state.error && !state.canSave) {
                    Text(
                        stringResource(R.string.app_routing_select_required),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.visibleApps, key = InstalledApp::packageName) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAction(AppRoutingAction.AppToggled(app.packageName)) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = app.packageName in state.selectedPackages,
                                onCheckedChange = {
                                    onAction(AppRoutingAction.AppToggled(app.packageName))
                                },
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.app_routing_all_apps_explanation),
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.error && state.canSave) {
                Text(stringResource(R.string.app_routing_save_failed), color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { onAction(AppRoutingAction.Save) },
                enabled = state.canSave && !state.isSaving,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                if (state.isSaving) CircularProgressIndicator()
                else Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun RowScope.RoutingModeChip(
    mode: AppRoutingMode,
    state: AppRoutingUiState,
    onAction: (AppRoutingAction) -> Unit,
) {
    FilterChip(
        selected = state.mode == mode,
        onClick = { onAction(AppRoutingAction.ModeChanged(mode)) },
        label = {
            Text(
                stringResource(
                    when (mode) {
                        AppRoutingMode.ALL_APPS -> R.string.app_routing_all_apps
                        AppRoutingMode.ONLY_SELECTED -> R.string.app_routing_only_selected
                        AppRoutingMode.BYPASS_SELECTED -> R.string.app_routing_bypass_selected
                    },
                ),
            )
        },
        modifier = Modifier.weight(1f),
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun AppRoutingPreview() {
    ModernSocksTheme(dynamicColor = false) {
        AppRoutingScreen(
            state = AppRoutingUiState(
                apps = listOf(
                    InstalledApp("com.example.browser", "Browser", false),
                    InstalledApp("com.example.mail", "Mail", false),
                ),
                mode = AppRoutingMode.ONLY_SELECTED,
                selectedPackages = setOf("com.example.browser"),
                isLoading = false,
            ),
            onAction = {},
            onBack = {},
        )
    }
}
