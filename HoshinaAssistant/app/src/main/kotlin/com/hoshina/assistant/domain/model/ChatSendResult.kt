package com.hoshina.assistant.domain.model

data class ChatSendResult(
    val displayReply: String,
    val rawReply: String,
    val agentsUsed: List<String> = emptyList(),
    val mode: String? = null,
)
