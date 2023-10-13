package com.nolanbarry.gateway.model

data class Event(
    val action: String,
    val content: Chat
)

data class Chat(
    val text: String,
    val bold: Boolean?,
    val italic: Boolean?,
    val underline: Boolean?,
    val strikethrough: Boolean?,
    val obfuscated: Boolean?,
    val font: String?,
    val color: String?,
    val clickEvent: Event?,
    val hoverEvent: Event?,
    val extra: List<Chat>?
)