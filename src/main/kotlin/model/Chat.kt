package com.nolanbarry.gateway.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class Event(
    val action: String,
    val content: Chat
)

/** Deserialize a [Chat] object from a json object (same as Chat.serializer) or from a string. */
class StringToChatDeserializer : JsonTransformingSerializer<Chat>(Chat.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return if (element is JsonPrimitive)
            buildJsonObject { put("text", element.jsonPrimitive.content) }
        else element
    }
}

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