@file:OptIn(ExperimentalSerializationApi::class)

package com.nolanbarry.gateway.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class Version(
    /** Can be anything but typically just the version number (1.20.1, Spigot 1.20.1, etc.) */
    val name: String,
    /** The exact protocol number. 1.20.1's protocol number is 763 */
    val protocol: Int
)

@Serializable
data class Player(
    /** The name of the player*/
    val name: String,
    /** A correctly formatted uuid of the player (8-4-4-4-12 hexadecimal format) */
    val id: String
)

@Serializable
data class Players(
    /** The maximum number of players allowed on the server */
    val max: Int,
    /** The number of players currently on the server */
    val online: Int,
    /** A sample of online players. Optional. Users can see the players listed when the hover over the
     * player count in the server list. */
    val sample: List<Player>? = null
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class ServerState(
    val version: Version,

    /** The message to display in the server list (aka as "message of the day" or "motd") */
    @Serializable(with = StringToChatDeserializer::class)
    val description: Chat,
    val players: Players,
    /** A base64-encoded 64x64 icon to display in the server list */
    val favicon: String? = null,
    @EncodeDefault
    val previewsChat: Boolean = true,
    @EncodeDefault
    val enforcesSecureChat: Boolean = true
)