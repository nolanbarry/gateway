package com.nolanbarry.gateway.protocol.packet

import com.nolanbarry.gateway.model.Buffer
import com.nolanbarry.gateway.model.Endian
import com.nolanbarry.gateway.model.IncompleteBufferException
import com.nolanbarry.gateway.model.readUInt

val LEGACY_PACKET_SIGNATURE = listOf(
    0xfe, 0x01, 0xfa, 0x00, 0x0b, 0x00, 0x4d, 0x00, 0x43,
    0x00, 0x7c, 0x00, 0x50, 0x00, 0x69, 0x00, 0x6e, 0x00,
    0x67, 0x00, 0x48, 0x00, 0x6f, 0x00, 0x73, 0x00, 0x74
)

const val MINIMUM_KNOWN_LEGACY_PACKET_SIZE = 29

/** See https://wiki.vg/Server_List_Ping#1.6. Servers should still support this type of packet as clients
 * send it when an initial ping fails. Returns a packet with the id `0xFE` if a legacy server list ping
 * packet was identified, or null if a legacy SLP packet was not found in the buffer.
 * @throws IncompleteBufferException If the buffer contains an incomplete legacy SLP packet.
 */
fun parseLegacyServerListPing(buffer: Buffer): Packet? {
    if (buffer.size < LEGACY_PACKET_SIGNATURE.size) return null
    if (buffer.subList(0, LEGACY_PACKET_SIGNATURE.size) == LEGACY_PACKET_SIGNATURE) return null
    if (buffer.size < MINIMUM_KNOWN_LEGACY_PACKET_SIZE) throw IncompleteBufferException()
    val lengthOfRemainingPayload = buffer.readUInt(27, 16, Endian.BIG).toInt()
    val totalPacketLength = MINIMUM_KNOWN_LEGACY_PACKET_SIZE + lengthOfRemainingPayload
    if (buffer.size < totalPacketLength) throw IncompleteBufferException()
    return Packet(
        id = 0xfe,
        totalLength = totalPacketLength,
        payload = buffer.subList(MINIMUM_KNOWN_LEGACY_PACKET_SIZE, totalPacketLength)
    )
}