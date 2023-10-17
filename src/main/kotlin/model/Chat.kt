package com.nolanbarry.gateway.model

import kotlinx.serialization.Serializable

@Serializable
data class Event(
    val action: String,
    val content: Chat
)

@Serializable
data class Chat(
    val text: String,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strikethrough: Boolean? = null,
    val obfuscated: Boolean? = null,
    val font: String? = null,
    val color: String? = null,
    val clickEvent: Event? = null,
    val hoverEvent: Event? = null,
    val extra: List<Chat>? = null
)