package com.nolanbarry.gateway.model

import kotlinx.serialization.Serializable

@Serializable
data class Version(
    /** Can be anything but typically just the version number (1.20.1, Spigot 1.20.1, etc.) */
    var name: String,
    /** The exact protocol number. 1.20.1's protocol number is 763 */
    var protocol: Int
)

@Serializable
data class Player(
    /** The name of the player*/
    var name: String,
    /** A correctly formatted uuid of the player (8-4-4-4-12 hexadecimal format) */
    var id: String
)

@Serializable
data class Players(
    /** The maximum number of players allowed on the server */
    var max: Int,
    /** The number of players currently on the server */
    var online: Int,
    /** A sample of online players. Optional. Users can see the players listed when the hover over the
     * player count in the server list. */
    var sample: List<Player> = emptyList()
)

@Serializable
data class ServerState(
    var version: Version,
    /** The message to display in the server list (aka motd) */
    var description: Chat,
    var players: Players,
    /** A base64-encoded 64x64 icon to display in the server list */
    var favicon: String? = null,
    var previewsChat: Boolean? = null,
    var enforcesSecureChat: Boolean? = null
)