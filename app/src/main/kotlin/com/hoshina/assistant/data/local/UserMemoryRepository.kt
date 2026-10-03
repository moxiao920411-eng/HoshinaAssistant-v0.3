package com.hoshina.assistant.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.userMemoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_memory",
)

interface UserMemoryRepository {
    val manualMemory: Flow<String>
    val autoMemory: Flow<String>
    val userAvatarUri: Flow<String>
    suspend fun getManualMemory(): String
    suspend fun getAutoMemory(): String
    suspend fun getCombinedMemory(): String
    suspend fun getUserAvatarUri(): String
    suspend fun setManualMemory(text: String)
    suspend fun setUserAvatarUri(uri: String)
    suspend fun rememberFromUserMessage(text: String): Boolean
}

class UserMemoryRepositoryImpl(
    private val context: Context,
) : UserMemoryRepository {

    override val manualMemory: Flow<String> = context.userMemoryDataStore.data.map { prefs ->
        prefs[MANUAL_MEMORY_KEY].orEmpty().ifBlank { prefs[LEGACY_PROFILE_MEMORY_KEY].orEmpty() }
    }

    override val autoMemory: Flow<String> = context.userMemoryDataStore.data.map { prefs ->
        prefs[AUTO_MEMORY_KEY].orEmpty()
    }

    override val userAvatarUri: Flow<String> = context.userMemoryDataStore.data.map { prefs ->
        prefs[USER_AVATAR_URI_KEY].orEmpty()
    }

    override suspend fun getManualMemory(): String = manualMemory.first()

    override suspend fun getAutoMemory(): String = autoMemory.first()

    override suspend fun getCombinedMemory(): String {
        val manual = getManualMemory().trim()
        val auto = getAutoMemory().trim()
        return listOf(
            manual.takeIf { it.isNotBlank() }?.let { "Manual user memory:\n$it" },
            auto.takeIf { it.isNotBlank() }?.let { "Auto conversation memory:\n$it" },
        ).filterNotNull().joinToString("\n\n")
    }

    override suspend fun getUserAvatarUri(): String = userAvatarUri.first()

    override suspend fun setManualMemory(text: String) {
        context.userMemoryDataStore.edit { prefs ->
            prefs[MANUAL_MEMORY_KEY] = text.trim()
        }
    }

    override suspend fun setUserAvatarUri(uri: String) {
        context.userMemoryDataStore.edit { prefs ->
            prefs[USER_AVATAR_URI_KEY] = uri.trim()
        }
    }

    override suspend fun rememberFromUserMessage(text: String): Boolean {
        val facts = extractUserFacts(text)
        if (facts.isEmpty()) return false

        val current = getAutoMemory()
        val existingLines = current
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        val newFacts = facts.filterNot { it in existingLines }
        if (newFacts.isEmpty()) return false

        val updated = buildString {
            if (current.isNotBlank()) {
                append(current.trim())
                append('\n')
            }
            newFacts.forEach { fact ->
                append(fact)
                append('\n')
            }
        }.trim()

        context.userMemoryDataStore.edit { prefs ->
            prefs[AUTO_MEMORY_KEY] = updated
        }
        return true
    }

    private fun extractUserFacts(text: String): List<String> {
        val normalized = text.trim().replace('\n', ' ')
        if (normalized.length < 4) return emptyList()

        val patterns = listOf(
            Regex("""(?:我叫|我的名字是|叫我)\s*([^，。,.!?\s]{1,24})"""),
            Regex("""(?:我喜歡|我偏好|我習慣|我不喜歡|我討厭)\s*([^，。,.!?]{2,48})"""),
            Regex("""(?:I'm|I am|My name is)\s+([^,.!?]{1,40})""", RegexOption.IGNORE_CASE),
            Regex("""(?:I like|I prefer|I dislike)\s+([^,.!?]{2,48})""", RegexOption.IGNORE_CASE),
            Regex("""(?:私は|私の名前は)\s*([^、。,.!?]{1,32})"""),
            Regex("""(?:好き|苦手|嫌い)なのは\s*([^、。,.!?]{1,48})"""),
        )

        return patterns
            .flatMap { pattern -> pattern.findAll(normalized).map { it.value.trim() } }
            .distinct()
            .map { "- $it" }
            .take(MAX_AUTO_FACTS_PER_MESSAGE)
    }

    companion object {
        private val LEGACY_PROFILE_MEMORY_KEY = stringPreferencesKey("profile_memory")
        private val MANUAL_MEMORY_KEY = stringPreferencesKey("manual_memory")
        private val AUTO_MEMORY_KEY = stringPreferencesKey("auto_memory")
        private val USER_AVATAR_URI_KEY = stringPreferencesKey("user_avatar_uri")
        private const val MAX_AUTO_FACTS_PER_MESSAGE = 4
    }
}
