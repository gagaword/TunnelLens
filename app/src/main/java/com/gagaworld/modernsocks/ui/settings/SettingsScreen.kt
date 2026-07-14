package com.gagaworld.modernsocks.ui.settings

import androidx.annotation.StringRes
import androidx.annotation.RawRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gagaworld.modernsocks.R
import com.gagaworld.modernsocks.data.preferences.AppSettings
import com.gagaworld.modernsocks.data.preferences.ThemeMode
import com.gagaworld.modernsocks.ui.theme.ModernSocksTheme
import com.gagaworld.modernsocks.data.locale.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    settings: AppSettings,
    language: AppLanguage,
    onLanguageChanged: (AppLanguage) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAutoConnectChanged: (Boolean) -> Unit,
    onExpandAdvancedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLicense by rememberSaveable { mutableStateOf(false) }
    var showThirdPartyNotices by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val sourceCodeUrl = stringResource(R.string.source_code_url)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsSection(
            title = stringResource(R.string.settings_language_title),
            body = stringResource(R.string.settings_language_body),
        ) {
            AppLanguage.entries.forEach { option ->
                LanguageRow(
                    language = option,
                    selected = language == option,
                    onClick = { onLanguageChanged(option) },
                )
            }
        }
        SettingsSection(
            title = stringResource(R.string.settings_appearance_title),
            body = stringResource(R.string.settings_appearance_body),
        ) {
            Text(
                text = stringResource(R.string.settings_theme_label),
                style = MaterialTheme.typography.labelLarge,
            )
            ThemeMode.entries.forEach { mode ->
                ThemeModeRow(
                    mode = mode,
                    selected = settings.themeMode == mode,
                    onClick = { onThemeModeChanged(mode) },
                )
            }
            HorizontalDivider()
            SettingSwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                description = stringResource(R.string.settings_dynamic_color_description),
                checked = settings.dynamicColor,
                onCheckedChange = onDynamicColorChanged,
            )
        }
        SettingsSection(title = stringResource(R.string.settings_behavior_title)) {
            SettingSwitchRow(
                title = stringResource(R.string.settings_auto_connect),
                description = stringResource(R.string.settings_auto_connect_description),
                checked = settings.autoConnect,
                onCheckedChange = onAutoConnectChanged,
            )
            HorizontalDivider()
            SettingSwitchRow(
                title = stringResource(R.string.settings_expand_advanced),
                description = stringResource(R.string.settings_expand_advanced_description),
                checked = settings.expandAdvancedByDefault,
                onCheckedChange = onExpandAdvancedChanged,
            )
        }
        SettingsSection(
            title = stringResource(R.string.settings_security_title),
            body = stringResource(R.string.settings_security_body),
        ) {}
        SettingsSection(
            title = stringResource(R.string.settings_open_source_title),
            body = stringResource(R.string.settings_open_source_body),
        ) {
            TextButton(onClick = { uriHandler.openUri(sourceCodeUrl) }) {
                Text(stringResource(R.string.settings_open_source_view_source))
            }
            TextButton(onClick = { showLicense = true }) {
                Text(stringResource(R.string.settings_open_source_view_license))
            }
            TextButton(onClick = { showThirdPartyNotices = true }) {
                Text(stringResource(R.string.settings_open_source_view_notices))
            }
        }
        SettingsSection(
            title = stringResource(R.string.settings_phase_title),
            body = stringResource(R.string.settings_phase_body),
        ) {}
    }

    if (showLicense) {
        LegalTextDialog(
            rawRes = R.raw.gpl_3_0,
            titleRes = R.string.settings_open_source_license_title,
            onDismiss = { showLicense = false },
        )
    }
    if (showThirdPartyNotices) {
        LegalTextDialog(
            rawRes = R.raw.third_party_notices,
            titleRes = R.string.settings_open_source_notices_title,
            onDismiss = { showThirdPartyNotices = false },
        )
    }
}

@Composable
private fun LegalTextDialog(
    @RawRes rawRes: Int,
    @StringRes titleRes: Int,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    val failureText = stringResource(R.string.settings_open_source_license_failed)
    val licenseText by produceState<String?>(
        initialValue = null,
        resources,
        rawRes,
        failureText,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                resources.openRawResource(rawRes)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
            }.getOrElse { failureText }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                contentAlignment = Alignment.Center,
            ) {
                val text = licenseText
                if (text == null) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = text,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun SettingsSection(
    title: String,
    body: String? = null,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (body != null) {
                Text(
                    text = body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            content()
        }
    }
}

@Composable
private fun ThemeModeRow(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = stringResource(mode.labelRes),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun LanguageRow(
    language: AppLanguage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = stringResource(language.labelRes),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private val AppLanguage.labelRes: Int
    @StringRes get() = when (this) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.SIMPLIFIED_CHINESE -> R.string.settings_language_simplified_chinese
        AppLanguage.ENGLISH -> R.string.settings_language_english
    }

private val ThemeMode.labelRes: Int
    @StringRes get() = when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

@Composable
private fun SettingSwitchRow(
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
            Text(text = title)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ModernSocksTheme(dynamicColor = false) {
        SettingsScreen(
            settings = AppSettings(),
            language = AppLanguage.SYSTEM,
            onLanguageChanged = {},
            onThemeModeChanged = {},
            onDynamicColorChanged = {},
            onAutoConnectChanged = {},
            onExpandAdvancedChanged = {},
        )
    }
}
