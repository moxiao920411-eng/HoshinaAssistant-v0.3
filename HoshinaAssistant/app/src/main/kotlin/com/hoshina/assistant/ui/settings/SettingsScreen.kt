package com.hoshina.assistant.ui.settings

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoshina.assistant.R
import com.hoshina.assistant.data.local.SettingsRepositoryImpl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    var premiumPassword by remember { mutableStateOf("") }
    val context = LocalContext.current
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        viewModel.onUserAvatarSelected(uri.toString())
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.titleMedium,
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LanguageButton(
                    label = stringResource(R.string.settings_language_zh),
                    selected = uiState.appLanguage == SettingsRepositoryImpl.APP_LANGUAGE_ZH,
                    onClick = { viewModel.onLanguageChange(SettingsRepositoryImpl.APP_LANGUAGE_ZH) },
                    modifier = Modifier.weight(1f),
                )
                LanguageButton(
                    label = stringResource(R.string.settings_language_en),
                    selected = uiState.appLanguage == SettingsRepositoryImpl.APP_LANGUAGE_EN,
                    onClick = { viewModel.onLanguageChange(SettingsRepositoryImpl.APP_LANGUAGE_EN) },
                    modifier = Modifier.weight(1f),
                )
                LanguageButton(
                    label = stringResource(R.string.settings_language_ja),
                    selected = uiState.appLanguage == SettingsRepositoryImpl.APP_LANGUAGE_JA,
                    onClick = { viewModel.onLanguageChange(SettingsRepositoryImpl.APP_LANGUAGE_JA) },
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = stringResource(R.string.settings_ai_title),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = uiState.aiName,
                onValueChange = viewModel::onAiNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_ai_name_label)) },
                singleLine = true,
                enabled = !uiState.isSaving,
            )
            OutlinedTextField(
                value = uiState.rolePrompt,
                onValueChange = viewModel::onRolePromptChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_role_prompt_label)) },
                placeholder = { Text(stringResource(R.string.settings_role_prompt_placeholder)) },
                minLines = 4,
                enabled = !uiState.isSaving,
            )

            Text(
                text = stringResource(R.string.settings_avatar_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarPreview(uri = uiState.userAvatarUri)
                Button(
                    onClick = { avatarPicker.launch(arrayOf("image/*")) },
                    enabled = !uiState.isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.settings_avatar_upload))
                }
            }

            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_dark_mode),
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(
                    checked = uiState.isDarkMode,
                    onCheckedChange = { viewModel.toggleDarkMode() },
                    enabled = !uiState.isSaving,
                )
            }

            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_show_device_time),
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(
                    checked = uiState.isDeviceTimeVisible,
                    onCheckedChange = { viewModel.toggleDeviceTimeVisible() },
                    enabled = !uiState.isSaving,
                )
            }

            Text(
                text = stringResource(R.string.settings_voice_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (uiState.isCloudTtsEnabled) {
                    stringResource(R.string.settings_voice_new_enabled)
                } else {
                    stringResource(R.string.settings_voice_old_enabled)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_voice_use_new),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = uiState.isCloudTtsEnabled,
                    onCheckedChange = { viewModel.toggleCloudTts() },
                    enabled = !uiState.isSaving,
                )
            }

            Text(
                text = stringResource(R.string.settings_memory_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_memory_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = uiState.profileMemory,
                onValueChange = viewModel::onProfileMemoryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_memory_label)) },
                placeholder = { Text(stringResource(R.string.settings_memory_placeholder)) },
                minLines = 3,
                enabled = !uiState.isSaving,
            )

            Text(
                text = stringResource(R.string.settings_auto_memory_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (uiState.isAutoMemoryEnabled) {
                    stringResource(R.string.settings_auto_memory_enabled)
                } else {
                    stringResource(R.string.settings_auto_memory_locked)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = uiState.autoMemory,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_auto_memory_label)) },
                minLines = 3,
                enabled = false,
            )
            if (uiState.isAutoMemoryEnabled) {
                Button(
                    onClick = viewModel::disableAutoMemory,
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_auto_memory_disable))
                }
            } else {
                OutlinedTextField(
                    value = premiumPassword,
                    onValueChange = { premiumPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_premium_password_label)) },
                    singleLine = true,
                    enabled = !uiState.isSaving,
                )
                Button(
                    onClick = { viewModel.unlockAutoMemory(premiumPassword) },
                    enabled = premiumPassword.isNotBlank() && !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_auto_memory_unlock))
                }
            }

            Text(
                text = stringResource(R.string.settings_api_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_api_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = uiState.apiUrl,
                onValueChange = viewModel::onApiUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_api_label)) },
                singleLine = true,
                enabled = !uiState.isSaving && !uiState.isDiscoveringBackend,
            )
            OutlinedButton(
                onClick = viewModel::discoverBackendUrl,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving && !uiState.isDiscoveringBackend,
            ) {
                if (uiState.isDiscoveringBackend) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(stringResource(R.string.settings_backend_discovery))
                }
            }

            Text(
                text = stringResource(R.string.settings_companion_debug_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_companion_debug_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.companionDebugMinMinutes,
                    onValueChange = viewModel::onCompanionDebugMinMinutesChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.settings_companion_debug_min)) },
                    singleLine = true,
                    enabled = !uiState.isSaving,
                )
                OutlinedTextField(
                    value = uiState.companionDebugMaxMinutes,
                    onValueChange = viewModel::onCompanionDebugMaxMinutesChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.settings_companion_debug_max)) },
                    singleLine = true,
                    enabled = !uiState.isSaving,
                )
            }

            Text(
                text = stringResource(R.string.settings_version_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_version_current, uiState.appVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            uiState.savedMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (uiState.isSaving || uiState.isDiscoveringBackend) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = viewModel::saveAll,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_save))
                }
            }
        }
    }
}

@Composable
private fun LanguageButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    }
}

@Composable
private fun AvatarPreview(uri: String) {
    val context = LocalContext.current
    val bitmap = remember(uri) {
        runCatching {
            if (uri.isBlank()) {
                null
            } else {
                context.contentResolver
                    .openInputStream(Uri.parse(uri))
                    ?.use(BitmapFactory::decodeStream)
            }
        }.getOrNull()
    }

    androidx.compose.material3.Surface(
        modifier = Modifier.size(64.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            androidx.compose.material3.Text(
                text = stringResource(R.string.settings_avatar_empty),
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
