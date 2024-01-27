package com.nolanbarry.gateway.protocol.packet

import com.nolanbarry.gateway.model.InvalidDataException
import com.nolanbarry.gateway.model.ServerState
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmName

typealias VarInt = Int

data class Packet<P : Any>(
    val id: VarInt,
    val payload: P
) {
    fun encode(): ByteBuffer {
        // Get a list of the fields in the payload by calling each property's getter
        val orderedPropertyNames = payload::class.primaryConstructor!!.parameters.map { it.name }
        val orderedProperties = orderedPropertyNames.map { name ->
            payload::class.memberProperties.find { it.name == name }
                ?: throw IllegalStateException("Primary constructor contains non-val arg")
        }
        val definition = orderedProperties.map { prop ->
            prop.getter.call(payload) ?: throw IllegalArgumentException("Packet property ${prop.name} is null")
        }
        val buffers = definition.map { field ->
            when (field) {
                is VarInt -> Field.VarInt.encode(field)
                is String -> Field.String.encode(field)
                is UByte -> Field.UnsignedByte.encode(field)
                is UShort -> Field.UnsignedShort.encode(field)
                is ULong -> Field.UnsignedLong.encode(field)
                is Long -> Field.SignedLong.encode(field)
                is ServerState -> Field.Json.encode(field)
                else -> throw InvalidDataException("Can't serialize class '${field::class.jvmName}'")
            }
        }
        val idBuffer = Field.VarInt.encode(id)
        val packetSize = buffers.sumOf { buff -> buff.limit() } + idBuffer.limit()
        val sizeBuffer = Field.VarInt.encode(packetSize)
        val concatenated = ByteBuffer.allocate(sizeBuffer.limit() + packetSize)
            .put(sizeBuffer)
            .put(idBuffer)
        buffers.forEach { buff -> concatenated.put(buff.position(0)) }
        concatenated.position(0)
        return concatenated
    }
}

data class RawPacket(
    val id: VarInt,
    /** Unparsed information unique to this packet */
    val payload: ByteBuffer
) {

    /** Formatted id */
    val fid = "0x${id.toString(16)}"

    companion object {
        /** Read a `RawPacket` from the beginning (position 0) of the buffer. If the buffer doesn't contain an entire packet,
         * returns `null`. If a packet is returned, it will be removed from the passed [buffer] and [buffer]'s
         * position will be equal to the number of unused bytes still in the buffer. */
        fun from(buffer: ByteBuffer): RawPacket? {
            if (buffer.position() == 0) return null
            val window = buffer.asReadOnlyBuffer().position(0).limit(buffer.position())
            try {
                parseLegacyServerListPing(window)?.apply { return this }

                val payloadLength = Field.VarInt.parse(window)
                if (payloadLength == 0)
                    throw InvalidDataException("Invalid packet")
                val lengthOfLength = window.position()
                val id = Field.VarInt.parse(window)
                val lengthOfId = window.position() - lengthOfLength
                val actualPayloadLength = payloadLength - lengthOfId

                val payload = ByteBuffer.allocate(actualPayloadLength)
                repeat(actualPayloadLength) { payload.put(window.get()) }
                payload.position(0)

                buffer.limit(buffer.position())
                buffer.position(window.position())
                buffer.compact()

                return RawPacket(id, payload)
            } catch (e: Exception) {
                if (e is BufferUnderflowException) return null
                else throw e
            }
        }
    }

    inline fun <reified T : Any> interpretAs(): Packet<T> {
        val definition = T::class.primaryConstructor!!.parameters
        val parameters: List<Any> = definition.map { field ->
            when (field.type.classifier) {
                VarInt::class -> Field.VarInt.parse(payload)
                String::class -> Field.String.parse(payload)
                UByte::class -> Field.UnsignedByte.parse(payload)
                UShort::class -> Field.UnsignedShort.parse(payload)
                ULong::class -> Field.UnsignedLong.parse(payload)
                Long::class -> Field.SignedLong.parse(payload)
                else -> Field.Json.parse(payload, field.type)
            }
        }

        val payload = T::class.primaryConstructor!!.call(args = parameters.toTypedArray())
        return Packet(this.id, payload)
    }
}

object Client {
    data class Handshake(
        val protocolVersion: VarInt,
        val serverAddress: String,
        val serverPort: UShort,
        val nextState: VarInt
    )

    class StatusRequest
    data class PingRequest(val pingPayload: Long)
}

object Server {
    data class StatusResponse(val response: ServerState)
    data class PingResponse(val pingPayload: Long)
}

