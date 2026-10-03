package com.hoshina.assistant.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hoshina.assistant.data.remote.RetrofitProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

interface SettingsRepository {
    val apiBaseUrl: Flow<String>
    val aiName: Flow<String>
    val aiModel: Flow<String>
    val rolePrompt: Flow<String>
    val appLanguage: Flow<String>
    val isAutoMemoryEnabled: Flow<Boolean>
    val isDeviceTimeVisible: Flow<Boolean>
    val isDarkMode: Flow<Boolean>
    val isCloudTtsEnabled: Flow<Boolean>
    val companionDebugMinMinutes: Flow<Int>
    val companionDebugMaxMinutes: Flow<Int>
    suspend fun getApiBaseUrl(): String
    suspend fun getAiName(): String
    suspend fun getAiModel(): String
    suspend fun getRolePrompt(): String
    suspend fun getAppLanguage(): String
    suspend fun getIsAutoMemoryEnabled(): Boolean
    suspend fun getIsDeviceTimeVisible(): Boolean
    suspend fun getIsDarkMode(): Boolean
    suspend fun getIsCloudTtsEnabled(): Boolean
    suspend fun getCompanionDebugMinMinutes(): Int
    suspend fun getCompanionDebugMaxMinutes(): Int
    suspend fun setApiBaseUrl(url: String)
    suspend fun setAiName(name: String)
    suspend fun setAiModel(model: String)
    suspend fun setRolePrompt(prompt: String)
    suspend fun setAppLanguage(language: String)
    suspend fun setIsAutoMemoryEnabled(enabled: Boolean)
    suspend fun setIsDeviceTimeVisible(enabled: Boolean)
    suspend fun setIsDarkMode(enabled: Boolean)
    suspend fun setIsCloudTtsEnabled(enabled: Boolean)
    suspend fun setCompanionDebugInterval(minMinutes: Int, maxMinutes: Int)
}

class SettingsRepositoryImpl(
    private val context: Context,
) : SettingsRepository {

    override val apiBaseUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_BASE_URL_KEY] ?: DEFAULT_API_BASE_URL
    }

    override val aiName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[AI_NAME_KEY] ?: DEFAULT_AI_NAME
    }

    override val aiModel: Flow<String> = context.dataStore.data.map { preferences ->
        normalizeAiModel(preferences[AI_MODEL_KEY])
    }

    override val rolePrompt: Flow<String> = context.dataStore.data.map { preferences ->
        normalizeRolePrompt(preferences[ROLE_PROMPT_KEY])
    }

    override val appLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_LANGUAGE_KEY] ?: DEFAULT_APP_LANGUAGE
    }

    override val isAutoMemoryEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_MEMORY_ENABLED_KEY] ?: false
    }

    override val isDeviceTimeVisible: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DEVICE_TIME_VISIBLE_KEY] ?: true
    }

    override val isDarkMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DARK_MODE_KEY] ?: false
    }

    override val isCloudTtsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[CLOUD_TTS_ENABLED_KEY] ?: true
    }

    override val companionDebugMinMinutes: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[COMPANION_DEBUG_MIN_MINUTES_KEY] ?: 0
    }

    override val companionDebugMaxMinutes: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[COMPANION_DEBUG_MAX_MINUTES_KEY] ?: 0
    }

    override suspend fun getApiBaseUrl(): String = apiBaseUrl.first()

    override suspend fun getAiName(): String = aiName.first()

    override suspend fun getAiModel(): String = aiModel.first()

    override suspend fun getRolePrompt(): String = rolePrompt.first()

    override suspend fun getAppLanguage(): String = appLanguage.first()

    override suspend fun getIsAutoMemoryEnabled(): Boolean = isAutoMemoryEnabled.first()

    override suspend fun getIsDeviceTimeVisible(): Boolean = isDeviceTimeVisible.first()

    override suspend fun getIsDarkMode(): Boolean = isDarkMode.first()

    override suspend fun getIsCloudTtsEnabled(): Boolean = isCloudTtsEnabled.first()

    override suspend fun getCompanionDebugMinMinutes(): Int = companionDebugMinMinutes.first()

    override suspend fun getCompanionDebugMaxMinutes(): Int = companionDebugMaxMinutes.first()

    override suspend fun setApiBaseUrl(url: String) {
        val normalized = RetrofitProvider.normalizeBaseUrl(url)
        context.dataStore.edit { preferences ->
            preferences[API_BASE_URL_KEY] = normalized
        }
    }

    override suspend fun setAiName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[AI_NAME_KEY] = name.trim().ifBlank { DEFAULT_AI_NAME }
        }
    }

    override suspend fun setAiModel(model: String) {
        context.dataStore.edit { preferences ->
            preferences[AI_MODEL_KEY] = model.trim().ifBlank { DEFAULT_AI_MODEL }
        }
    }

    override suspend fun setRolePrompt(prompt: String) {
        context.dataStore.edit { preferences ->
            preferences[ROLE_PROMPT_KEY] = prompt.trim().ifBlank { DEFAULT_ROLE_PROMPT }
        }
    }

    override suspend fun setAppLanguage(language: String) {
        val normalized = when (language.trim().lowercase()) {
            APP_LANGUAGE_EN, APP_LANGUAGE_JA, APP_LANGUAGE_ZH -> language.trim().lowercase()
            else -> DEFAULT_APP_LANGUAGE
        }
        context.dataStore.edit { preferences ->
            preferences[APP_LANGUAGE_KEY] = normalized
        }
    }

    override suspend fun setIsAutoMemoryEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_MEMORY_ENABLED_KEY] = enabled
        }
    }

    override suspend fun setIsDeviceTimeVisible(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DEVICE_TIME_VISIBLE_KEY] = enabled
        }
    }

    override suspend fun setIsDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = enabled
        }
    }

    override suspend fun setIsCloudTtsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[CLOUD_TTS_ENABLED_KEY] = enabled
        }
    }

    override suspend fun setCompanionDebugInterval(minMinutes: Int, maxMinutes: Int) {
        val min = minMinutes.coerceAtLeast(0)
        val max = maxMinutes.coerceAtLeast(0)
        context.dataStore.edit { preferences ->
            preferences[COMPANION_DEBUG_MIN_MINUTES_KEY] = min
            preferences[COMPANION_DEBUG_MAX_MINUTES_KEY] = max
        }
    }

    companion object {
        const val DEFAULT_API_BASE_URL = "http://10.128.237.121:8000/"
        const val DEFAULT_AI_NAME = "Hoshina"
        const val DEFAULT_AI_MODEL = "deepseek-r1:8b"
        private const val LEGACY_DEFAULT_AI_MODEL = "qwen2.5:1.5b"
        const val DEFAULT_ROLE_PROMPT = "\u5982\u679c\u8a18\u61b6\u4e2d\u6709\u76f8\u95dc\u8cc7\u6599\uff0c\u512a\u5148\u56de\u7b54\u8a18\u61b6\u4e2d\u7684\u5167\u5bb9\u3002\u56de\u7b54\u4e0d\u8d85\u904e30\u500b\u5b57\u3002"
        private const val LEGACY_DEFAULT_ROLE_PROMPT = "You are Hoshina, a warm and reliable AI assistant. Reply in Traditional Chinese, remember user preferences, and keep answers clear."
        const val APP_LANGUAGE_ZH = "zh"
        const val APP_LANGUAGE_EN = "en"
        const val APP_LANGUAGE_JA = "ja"
        const val DEFAULT_APP_LANGUAGE = APP_LANGUAGE_ZH
        private val API_BASE_URL_KEY = stringPreferencesKey("api_base_url")
        private val AI_NAME_KEY = stringPreferencesKey("ai_name")
        private val AI_MODEL_KEY = stringPreferencesKey("ai_model")
        private val ROLE_PROMPT_KEY = stringPreferencesKey("role_prompt")
        private val APP_LANGUAGE_KEY = stringPreferencesKey("app_language")
        private val AUTO_MEMORY_ENABLED_KEY = booleanPreferencesKey("auto_memory_enabled")
        private val DEVICE_TIME_VISIBLE_KEY = booleanPreferencesKey("device_time_visible")
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        private val CLOUD_TTS_ENABLED_KEY = booleanPreferencesKey("cloud_tts_enabled")
        private val COMPANION_DEBUG_MIN_MINUTES_KEY = intPreferencesKey("companion_debug_min_minutes")
        private val COMPANION_DEBUG_MAX_MINUTES_KEY = intPreferencesKey("companion_debug_max_minutes")

        private fun normalizeRolePrompt(prompt: String?): String {
            val normalized = prompt?.trim().orEmpty()
            return when {
                normalized.isBlank() -> DEFAULT_ROLE_PROMPT
                normalized == LEGACY_DEFAULT_ROLE_PROMPT -> DEFAULT_ROLE_PROMPT
                else -> normalized
            }
        }

        private fun normalizeAiModel(model: String?): String {
            val normalized = model?.trim().orEmpty()
            return if (normalized.isBlank() || normalized == LEGACY_DEFAULT_AI_MODEL) {
                DEFAULT_AI_MODEL
            } else {
                normalized
            }
        }
    }
}
