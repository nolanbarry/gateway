package com.nolanbarry.gateway.protocol.packet

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

val LEGACY_PACKET_SIGNATURE: ByteBuffer = ByteBuffer.wrap(
    listOf(
        0xfe, 0x01, 0xfa, 0x00, 0x0b, 0x00, 0x4d, 0x00, 0x43,
        0x00, 0x7c, 0x00, 0x50, 0x00, 0x69, 0x00, 0x6e, 0x00,
        0x67, 0x00, 0x48, 0x00, 0x6f, 0x00, 0x73, 0x00, 0x74
    ).map(Int::toByte).toByteArray()
).asReadOnlyBuffer()

val LEGACY_SERVER_RESPONSE: ByteBuffer = ByteBuffer.wrap(
    listOf(
        0xff, 0x00, 0x23, 0x00, 0xa7, 0x00, 0x31, 0x00, 0x00, 0x00, 0x34, 0x00, 0x37, 0x00, 0x00, 0x00,
        0x31, 0x00, 0x2e, 0x00, 0x34, 0x00, 0x2e, 0x00, 0x32, 0x00, 0x00, 0x00, 0x41, 0x00, 0x20, 0x00,
        0x4d, 0x00, 0x69, 0x00, 0x6e, 0x00, 0x65, 0x00, 0x63, 0x00, 0x72, 0x00, 0x61, 0x00, 0x66, 0x00,
        0x74, 0x00, 0x20, 0x00, 0x53, 0x00, 0x65, 0x00, 0x72, 0x00, 0x76, 0x00, 0x65, 0x00, 0x72, 0x00,
        0x00, 0x00, 0x30, 0x00, 0x00, 0x00, 0x32, 0x00, 0x30
    ).map(Int::toByte).toByteArray()
).asReadOnlyBuffer()

const val MINIMUM_KNOWN_LEGACY_PACKET_SIZE = 29

/** See https://wiki.vg/Server_List_Ping#1.6. Servers should still support this type of packet as clients
 * send it when an initial ping fails. Returns a packet with the id `0xFE` if a legacy server list ping
 * packet was identified, or null if a legacy SLP packet was not found in the buffer.
 * @throws BufferUnderflowException If the buffer contains an incomplete legacy SLP packet.
 */
fun parseLegacyServerListPing(buffer: ByteBuffer): RawPacket? {
    if (buffer.limit() < LEGACY_PACKET_SIGNATURE.limit()) return null
    if (buffer.compareTo(LEGACY_PACKET_SIGNATURE) != 0) return null
    if (buffer.limit() < MINIMUM_KNOWN_LEGACY_PACKET_SIZE) throw BufferUnderflowException()

    val lengthOfRemainingPayload = buffer.getShort(27)
    val totalPacketLength = MINIMUM_KNOWN_LEGACY_PACKET_SIZE + lengthOfRemainingPayload

    if (buffer.limit() < totalPacketLength) throw BufferUnderflowException()

    return RawPacket(
        id = 0xfe,
        payload = buffer.slice(MINIMUM_KNOWN_LEGACY_PACKET_SIZE, totalPacketLength)
    )
}