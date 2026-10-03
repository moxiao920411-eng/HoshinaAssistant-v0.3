package com.hoshina.assistant.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hoshina.assistant.R
import com.hoshina.assistant.data.local.SettingsRepository
import com.hoshina.assistant.data.local.UserMemoryRepository
import com.hoshina.assistant.data.remote.BackendUrlResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiUrl: String = "",
    val aiName: String = "",
    val aiModel: String = "",
    val rolePrompt: String = "",
    val appLanguage: String = "",
    val profileMemory: String = "",
    val autoMemory: String = "",
    val userAvatarUri: String = "",
    val isAutoMemoryEnabled: Boolean = false,
    val isDeviceTimeVisible: Boolean = true,
    val isDarkMode: Boolean = false,
    val isCloudTtsEnabled: Boolean = true,
    val companionDebugMinMinutes: String = "",
    val companionDebugMaxMinutes: String = "",
    val savedMessage: String? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
    val isDiscoveringBackend: Boolean = false,
    val appVersion: String = "",
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val userMemoryRepository: UserMemoryRepository,
    application: Application,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val url = settingsRepository.getApiBaseUrl()
            val aiName = settingsRepository.getAiName()
            val aiModel = settingsRepository.getAiModel()
            val rolePrompt = settingsRepository.getRolePrompt()
            val appLanguage = settingsRepository.getAppLanguage()
            val memory = userMemoryRepository.getManualMemory()
            val autoMemory = userMemoryRepository.getAutoMemory()
            val userAvatarUri = userMemoryRepository.getUserAvatarUri()
            val autoMemoryEnabled = settingsRepository.getIsAutoMemoryEnabled()
            val deviceTimeVisible = settingsRepository.getIsDeviceTimeVisible()
            val darkMode = settingsRepository.getIsDarkMode()
            val cloudTtsEnabled = settingsRepository.getIsCloudTtsEnabled()
            val debugMin = settingsRepository.getCompanionDebugMinMinutes()
            val debugMax = settingsRepository.getCompanionDebugMaxMinutes()
            val appVersion = resolveAppVersion()
            _uiState.update {
                it.copy(
                    apiUrl = url,
                    aiName = aiName,
                    aiModel = aiModel,
                    rolePrompt = rolePrompt,
                    appLanguage = appLanguage,
                    profileMemory = memory,
                    autoMemory = autoMemory,
                    userAvatarUri = userAvatarUri,
                    isAutoMemoryEnabled = autoMemoryEnabled,
                    isDeviceTimeVisible = deviceTimeVisible,
                    isDarkMode = darkMode,
                    isCloudTtsEnabled = cloudTtsEnabled,
                    companionDebugMinMinutes = debugMin.takeIf { it > 0 }?.toString().orEmpty(),
                    companionDebugMaxMinutes = debugMax.takeIf { it > 0 }?.toString().orEmpty(),
                    appVersion = appVersion,
                )
            }
        }
    }

    fun onApiUrlChange(url: String) {
        _uiState.update { it.copy(apiUrl = url, savedMessage = null, error = null) }
    }

    fun onProfileMemoryChange(text: String) {
        _uiState.update { it.copy(profileMemory = text, savedMessage = null, error = null) }
    }

    fun onAiNameChange(name: String) {
        _uiState.update { it.copy(aiName = name, savedMessage = null, error = null) }
    }

    fun onAiModelChange(model: String) {
        _uiState.update { it.copy(aiModel = model, savedMessage = null, error = null) }
    }

    fun onRolePromptChange(prompt: String) {
        _uiState.update { it.copy(rolePrompt = prompt, savedMessage = null, error = null) }
    }

    fun onCompanionDebugMinMinutesChange(value: String) {
        _uiState.update {
            it.copy(
                companionDebugMinMinutes = value.filter(Char::isDigit).take(4),
                savedMessage = null,
                error = null,
            )
        }
    }

    fun onCompanionDebugMaxMinutesChange(value: String) {
        _uiState.update {
            it.copy(
                companionDebugMaxMinutes = value.filter(Char::isDigit).take(4),
                savedMessage = null,
                error = null,
            )
        }
    }

    fun onUserAvatarSelected(uri: String) {
        _uiState.update { it.copy(userAvatarUri = uri, savedMessage = null, error = null) }
        viewModelScope.launch {
            userMemoryRepository.setUserAvatarUri(uri)
        }
    }

    fun onLanguageChange(language: String) {
        _uiState.update { it.copy(appLanguage = language, savedMessage = null, error = null) }
        viewModelScope.launch {
            settingsRepository.setAppLanguage(language)
        }
    }

    fun discoverBackendUrl() {
        val currentUrl = _uiState.value.apiUrl
        if (currentUrl.isBlank()) {
            _uiState.update {
                it.copy(error = getApplication<Application>().getString(R.string.settings_api_empty))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDiscoveringBackend = true,
                    savedMessage = null,
                    error = null,
                )
            }

            val resolvedUrl = BackendUrlResolver.resolve(currentUrl)
            if (resolvedUrl == null) {
                _uiState.update {
                    it.copy(
                        isDiscoveringBackend = false,
                        error = getApplication<Application>().getString(
                            R.string.settings_backend_discovery_failed,
                        ),
                    )
                }
                return@launch
            }

            settingsRepository.setApiBaseUrl(resolvedUrl)
            _uiState.update {
                it.copy(
                    apiUrl = resolvedUrl,
                    isDiscoveringBackend = false,
                    savedMessage = getApplication<Application>().getString(
                        R.string.settings_backend_discovery_success,
                        resolvedUrl,
                    ),
                )
            }
        }
    }

    fun toggleDarkMode() {
        val enabled = !_uiState.value.isDarkMode
        _uiState.update { it.copy(isDarkMode = enabled, savedMessage = null, error = null) }
        viewModelScope.launch {
            settingsRepository.setIsDarkMode(enabled)
        }
    }

    fun toggleDeviceTimeVisible() {
        val enabled = !_uiState.value.isDeviceTimeVisible
        _uiState.update {
            it.copy(isDeviceTimeVisible = enabled, savedMessage = null, error = null)
        }
        viewModelScope.launch {
            settingsRepository.setIsDeviceTimeVisible(enabled)
        }
    }

    fun toggleCloudTts() {
        val enabled = !_uiState.value.isCloudTtsEnabled
        _uiState.update {
            it.copy(isCloudTtsEnabled = enabled, savedMessage = null, error = null)
        }
        viewModelScope.launch {
            settingsRepository.setIsCloudTtsEnabled(enabled)
        }
    }

    fun unlockAutoMemory(password: String) {
        if (password != PREMIUM_TEST_PASSWORD) {
            _uiState.update {
                it.copy(error = getApplication<Application>().getString(R.string.settings_premium_password_error))
            }
            return
        }
        _uiState.update { it.copy(isAutoMemoryEnabled = true, savedMessage = null, error = null) }
        viewModelScope.launch {
            settingsRepository.setIsAutoMemoryEnabled(true)
        }
    }

    fun disableAutoMemory() {
        _uiState.update { it.copy(isAutoMemoryEnabled = false, savedMessage = null, error = null) }
        viewModelScope.launch {
            settingsRepository.setIsAutoMemoryEnabled(false)
        }
    }

    fun saveAll() {
        val url = _uiState.value.apiUrl
        if (url.isBlank()) {
            _uiState.update {
                it.copy(error = getApplication<Application>().getString(R.string.settings_api_empty))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, savedMessage = null) }
            try {
                val debugMin = _uiState.value.companionDebugMinMinutes.toIntOrNull() ?: 0
                val debugMax = _uiState.value.companionDebugMaxMinutes.toIntOrNull() ?: 0
                if (debugMin > 0 && debugMax > 0 && debugMin > debugMax) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = getApplication<Application>().getString(
                                R.string.settings_companion_debug_interval_error,
                            ),
                        )
                    }
                    return@launch
                }

                settingsRepository.setApiBaseUrl(url)
                settingsRepository.setAiName(_uiState.value.aiName)
                settingsRepository.setAiModel(_uiState.value.aiModel)
                settingsRepository.setRolePrompt(_uiState.value.rolePrompt)
                settingsRepository.setAppLanguage(_uiState.value.appLanguage)
                settingsRepository.setIsDeviceTimeVisible(_uiState.value.isDeviceTimeVisible)
                settingsRepository.setIsAutoMemoryEnabled(_uiState.value.isAutoMemoryEnabled)
                settingsRepository.setIsDarkMode(_uiState.value.isDarkMode)
                settingsRepository.setIsCloudTtsEnabled(_uiState.value.isCloudTtsEnabled)
                settingsRepository.setCompanionDebugInterval(debugMin, debugMax)
                userMemoryRepository.setManualMemory(_uiState.value.profileMemory)
                val savedUrl = settingsRepository.getApiBaseUrl()
                val savedName = settingsRepository.getAiName()
                val savedModel = settingsRepository.getAiModel()
                val savedPrompt = settingsRepository.getRolePrompt()
                val savedLanguage = settingsRepository.getAppLanguage()
                val savedManualMemory = userMemoryRepository.getManualMemory()
                val savedAutoMemory = userMemoryRepository.getAutoMemory()
                val savedUserAvatarUri = userMemoryRepository.getUserAvatarUri()
                val savedDebugMin = settingsRepository.getCompanionDebugMinMinutes()
                val savedDebugMax = settingsRepository.getCompanionDebugMaxMinutes()
                _uiState.update {
                    it.copy(
                        apiUrl = savedUrl,
                        aiName = savedName,
                        aiModel = savedModel,
                        rolePrompt = savedPrompt,
                        appLanguage = savedLanguage,
                        profileMemory = savedManualMemory,
                        autoMemory = savedAutoMemory,
                        userAvatarUri = savedUserAvatarUri,
                        companionDebugMinMinutes = savedDebugMin.takeIf { minutes -> minutes > 0 }
                            ?.toString()
                            .orEmpty(),
                        companionDebugMaxMinutes = savedDebugMax.takeIf { minutes -> minutes > 0 }
                            ?.toString()
                            .orEmpty(),
                        isSaving = false,
                        savedMessage = getApplication<Application>().getString(R.string.settings_all_saved),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = e.message ?: getApplication<Application>().getString(
                            R.string.settings_save_failed,
                        ),
                    )
                }
            }
        }
    }

    companion object {
        private const val PREMIUM_TEST_PASSWORD = "1213"
    }

    private fun resolveAppVersion(): String {
        val app = getApplication<Application>()
        return runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
    }
}
