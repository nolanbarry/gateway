package com.nolanbarry.gateway.protocol.packet

import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

typealias VarInt = Int

abstract class Packet {
    open var id: Int = 0
    open lateinit var payload: List<Byte>
}

data class RawPacket (
    override var id: VarInt,
    /** Unparsed information unique to this packet */
    override var payload: List<Byte>
): Packet {
    /** Total length of the entire packet (including the bytes describing the length of the id and payload) */
    val totalLength: Int = payload.size + Field.VarInt.encode(id).size

    fun <T: Packet> interpretAs(packetType: KClass<T>): T {
        val definition = packetType.primaryConstructor!!.parameters
        val offset = Offset()
        val parameters: List<Any> = definition.map { field ->
            when (val type = field.type.classifier) {
                VarInt::class -> Field.VarInt.parse(payload, offset)
                String::class -> Field.String.parse(payload, offset)
                UByte::class -> Field.UnsignedByte.parse(payload, offset)
                UShort::class -> Field.UnsignedShort.parse(payload, offset)
                Long::class -> Field.Long.parse(payload, offset)
                else -> throw IllegalArgumentException("Unknown field $type")
            }
        }
        val packet = packetType.primaryConstructor!!.call(*parameters.toTypedArray())
        packet.id = id
        packet.payload = payload
        return packet
    }
}

object ClientPacketSchema {
    data class Handshake(
        val protocolVersion: VarInt,
        val serverAddress: String,
        val serverPort: UShort,
        val nextState: VarInt
    ) : Packet()
    class StatusRequest : Packet()
    data class PingRequest(val pingPayload: Long) : Packet()
}

object ServerPacketSchema {
    data class StatusResponse(val response: String) : Packet()
    data class PingResponse(val pingPayload: Long) : Packet()
}

interface Handshake {
    val protocolVersion: VarInt
    val serverAddress: String
    val serverPort: UShort
    val nextState: VarInt
}

val x = object {
    override get
}

