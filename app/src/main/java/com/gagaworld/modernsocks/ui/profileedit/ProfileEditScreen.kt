package com.gagaworld.modernsocks.ui.profileedit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gagaworld.modernsocks.R
import com.gagaworld.modernsocks.data.model.ProfileDraft
import com.gagaworld.modernsocks.data.model.ProfileField
import com.gagaworld.modernsocks.data.model.ValidationError
import com.gagaworld.modernsocks.data.preferences.SettingsRepository
import com.gagaworld.modernsocks.data.repository.ProfileRepository
import com.gagaworld.modernsocks.ui.theme.ModernSocksTheme

@Composable
fun ProfileEditRoute(
    profileId: Long?,
    profileRepository: ProfileRepository,
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onAppRouting: (Long) -> Unit,
    viewModel: ProfileEditViewModel = viewModel(key = "profile-edit-${profileId ?: "new"}") {
        ProfileEditViewModel(profileId, profileRepository, settingsRepository)
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.saveCompleted) {
        if (uiState.saveCompleted) onSaved()
    }
    ProfileEditScreen(
        uiState = uiState,
        isNewProfile = profileId == null,
        onAction = viewModel::onAction,
        onBack = onBack,
        onAppRouting = { profileId?.let(onAppRouting) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    uiState: ProfileEditUiState,
    isNewProfile: Boolean,
    onAction: (ProfileEditAction) -> Unit,
    onBack: () -> Unit,
    onAppRouting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDiscardConfirmation by rememberSaveable { mutableStateOf(false) }
    val requestBack = {
        when {
            uiState.isSaving -> Unit
            uiState.hasUnsavedChanges -> showDiscardConfirmation = true
            else -> onBack()
        }
    }
    BackHandler(enabled = uiState.isSaving || uiState.hasUnsavedChanges) {
        requestBack()
    }

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text(stringResource(R.string.profile_discard_changes_title)) },
            text = { Text(stringResource(R.string.profile_discard_changes_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmation = false
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.action_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isNewProfile) R.string.profile_edit_new_title
                            else R.string.profile_edit_existing_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = requestBack,
                        enabled = !uiState.isSaving,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onAction(ProfileEditAction.Save) },
                        enabled = !uiState.isLoading && !uiState.isSaving,
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            uiState.loadFailed -> ErrorContent(
                message = stringResource(R.string.profile_load_failed),
                onBack = onBack,
                modifier = Modifier.padding(padding),
            )
            else -> ProfileForm(
                uiState = uiState,
                onAction = onAction,
                onAppRouting = onAppRouting,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ProfileForm(
    uiState: ProfileEditUiState,
    onAction: (ProfileEditAction) -> Unit,
    onAppRouting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = uiState.draft
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (uiState.saveFailed) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.profile_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        ProfileSection(title = stringResource(R.string.profile_basic_section)) {
            ProfileTextField(
                value = draft.name,
                onValueChange = { onAction(ProfileEditAction.NameChanged(it)) },
                label = stringResource(R.string.profile_name_label),
                error = fieldError(ProfileField.NAME, uiState.errors),
            )
            ProfileTextField(
                value = draft.host,
                onValueChange = { onAction(ProfileEditAction.HostChanged(it)) },
                label = stringResource(R.string.profile_host_label),
                error = fieldError(ProfileField.HOST, uiState.errors),
            )
            ProfileTextField(
                value = draft.port,
                onValueChange = { onAction(ProfileEditAction.PortChanged(it)) },
                label = stringResource(R.string.profile_port_label),
                error = fieldError(ProfileField.PORT, uiState.errors),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            SwitchRow(
                title = stringResource(R.string.profile_authentication_label),
                description = stringResource(R.string.profile_authentication_description),
                checked = draft.authenticationEnabled,
                onCheckedChange = { onAction(ProfileEditAction.AuthenticationChanged(it)) },
            )
            if (draft.authenticationEnabled) {
                ProfileTextField(
                    value = draft.username,
                    onValueChange = { onAction(ProfileEditAction.UsernameChanged(it)) },
                    label = stringResource(R.string.profile_username_label),
                    error = fieldError(ProfileField.USERNAME, uiState.errors),
                )
                PasswordField(
                    draft = draft,
                    error = fieldError(ProfileField.PASSWORD, uiState.errors),
                    onValueChange = { onAction(ProfileEditAction.PasswordChanged(it)) },
                )
            }
        }

        TextButton(
            onClick = { onAction(ProfileEditAction.ToggleAdvanced) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (uiState.showAdvanced) R.string.action_hide_advanced
                    else R.string.action_show_advanced,
                ),
            )
        }

        if (uiState.showAdvanced) {
            ProfileSection(title = stringResource(R.string.profile_advanced_section)) {
                ProfileTextField(
                    value = draft.dnsServer,
                    onValueChange = { onAction(ProfileEditAction.DnsChanged(it)) },
                    label = stringResource(R.string.profile_dns_label),
                    error = fieldError(ProfileField.DNS, uiState.errors),
                    supportingText = stringResource(R.string.profile_dns_description),
                )
                ProfileTextField(
                    value = draft.mtu,
                    onValueChange = { onAction(ProfileEditAction.MtuChanged(it)) },
                    label = stringResource(R.string.profile_mtu_label),
                    error = fieldError(ProfileField.MTU, uiState.errors),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                SwitchRow(
                    title = stringResource(R.string.profile_bypass_lan_label),
                    description = stringResource(R.string.profile_bypass_lan_description),
                    checked = draft.bypassLan,
                    onCheckedChange = { onAction(ProfileEditAction.BypassLanChanged(it)) },
                )
                SwitchRow(
                    title = stringResource(R.string.profile_auto_reconnect_label),
                    description = stringResource(R.string.profile_auto_reconnect_description),
                    checked = draft.autoReconnect,
                    onCheckedChange = { onAction(ProfileEditAction.AutoReconnectChanged(it)) },
                )
                if (draft.id != null) {
                    TextButton(onClick = onAppRouting, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.app_routing_open))
                    }
                } else {
                    Text(
                        stringResource(R.string.app_routing_save_profile_first),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Button(
            onClick = { onAction(ProfileEditAction.Save) },
            enabled = !uiState.isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isSaving) CircularProgressIndicator()
            else Text(stringResource(R.string.action_save))
        }
    }
}

@Composable
private fun ProfileSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = error != null,
        supportingText = when {
            error != null -> ({ Text(error) })
            supportingText != null -> ({ Text(supportingText) })
            else -> null
        },
        keyboardOptions = keyboardOptions,
    )
}

@Composable
private fun PasswordField(
    draft: ProfileDraft,
    error: String?,
    onValueChange: (String) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = draft.password,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.profile_password_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = error != null,
        visualTransformation = if (visible) VisualTransformation.None
        else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(stringResource(if (visible) R.string.action_hide else R.string.action_show))
            }
        },
        supportingText = {
            Text(
                error ?: if (draft.hasStoredPassword && draft.password.isEmpty()) {
                    stringResource(R.string.profile_password_saved)
                } else {
                    ""
                },
            )
        },
    )
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun fieldError(
    field: ProfileField,
    errors: Map<ProfileField, ValidationError>,
): String? = when (errors[field]) {
    ValidationError.REQUIRED -> stringResource(R.string.validation_required)
    ValidationError.TOO_LONG -> stringResource(R.string.validation_too_long)
    ValidationError.INVALID_HOST -> stringResource(R.string.validation_invalid_host)
    ValidationError.INVALID_NUMBER -> stringResource(R.string.validation_invalid_number)
    ValidationError.OUT_OF_RANGE -> stringResource(
        if (field == ProfileField.PORT) R.string.validation_port_range
        else R.string.validation_mtu_range,
    )
    null -> null
}

@Composable
private fun ErrorContent(
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(R.string.action_back))
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ProfileEditPreview() {
    ModernSocksTheme(dynamicColor = false) {
        ProfileEditScreen(
            uiState = ProfileEditUiState(
                isLoading = false,
                showAdvanced = true,
                draft = ProfileDraft(
                    name = stringResource(R.string.preview_profile_name),
                    host = stringResource(R.string.preview_profile_host),
                    authenticationEnabled = true,
                    username = stringResource(R.string.preview_profile_username),
                    hasStoredPassword = true,
                ),
            ),
            isNewProfile = false,
            onAction = {},
            onBack = {},
            onAppRouting = {},
        )
    }
}
