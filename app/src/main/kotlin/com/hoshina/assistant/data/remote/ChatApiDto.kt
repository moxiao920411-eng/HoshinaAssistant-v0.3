package com.hoshina.assistant.data.remote

import com.google.gson.annotations.SerializedName

data class ChatHistoryDto(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String,
)

data class ChatRequestDto(
    @SerializedName("user_id") val userId: Int,
    @SerializedName("message") val message: String,
    @SerializedName("history") val history: List<ChatHistoryDto> = emptyList(),
    @SerializedName("user_profile") val userProfile: String? = null,
    @SerializedName("client_time") val clientTime: String? = null,
    @SerializedName("ai_name") val aiName: String? = null,
    @SerializedName("ai_model") val aiModel: String? = null,
    @SerializedName("role_prompt") val rolePrompt: String? = null,
    @SerializedName("voice_mode") val voiceMode: Boolean = false,
)

data class ChatResponseDto(
    @SerializedName("response") val response: String,
    @SerializedName("agents_used") val agentsUsed: List<String> = emptyList(),
    @SerializedName("mode") val mode: String? = null,
    @SerializedName("route_reason") val routeReason: String? = null,
)

data class AppVersionDto(
    @SerializedName("latest_version_code") val latestVersionCode: Long,
    @SerializedName("latest_version_name") val latestVersionName: String,
    @SerializedName("apk_url") val apkUrl: String? = null,
    @SerializedName("release_notes") val releaseNotes: String? = null,
)
