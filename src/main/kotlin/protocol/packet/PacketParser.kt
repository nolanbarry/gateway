package com.nolanbarry.gateway.protocol.packet

import com.nolanbarry.gateway.model.IncompleteBufferException

fun readPacket(buffer: List<Byte>): RawPacket? {
    try {
        parseLegacyServerListPing(buffer)?.apply { return this }

        val offset = Offset()
        val payloadLength = Field.VarInt.parse(buffer, offset)
        val lengthOfLength = offset.value
        val id = Field.VarInt.parse(buffer, offset)
        val totalPacketLength = payloadLength + lengthOfLength
        return RawPacket(
            id,
            buffer.slice(offset.value..<totalPacketLength).toList()
        )
    } catch (e: Exception) {
        if (e is IncompleteBufferException) return null
        else throw e
    }
}