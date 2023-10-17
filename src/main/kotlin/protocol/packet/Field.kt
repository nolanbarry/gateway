package com.nolanbarry.gateway.protocol.packet

import com.nolanbarry.gateway.model.InvalidDataException
import kotlinx.serialization.encodeToString
import java.nio.ByteBuffer
import kotlin.reflect.full.companionObject
import kotlinx.serialization.json.Json as KotlinxJson

interface FieldInterface<T> {
    fun parse(buffer: ByteBuffer): T
    fun encode(data: T): ByteBuffer
}

object JsonField {
    inline fun <reified T> parse(buffer: ByteBuffer): T = KotlinxJson.decodeFromString(Field.String.parse(buffer))
    inline fun <reified T> encode(data: T): ByteBuffer = Field.String.encode(KotlinxJson.encodeToString<T>(data))
}

object Field {
    private const val DATA_BITS_PER_BYTE = 7
    private const val BYTE_MASK: Long = 0xff
    private const val DATA_MASK: Long = BYTE_MASK ushr (Byte.SIZE_BITS - DATA_BITS_PER_BYTE)
    private const val CONTINUE_MASK: Long = BYTE_MASK shl DATA_BITS_PER_BYTE and BYTE_MASK

    val VarInt = object : FieldInterface<Int> {
        override fun parse(buffer: ByteBuffer): Int {
            buffer.mark()
            var result = 0L
            var significantBitsRead = 0
            do {
                if (significantBitsRead >= 32) throw InvalidDataException("VarInt is too big")

                val nextByte = buffer.get().toLong()
                val dataBits = nextByte and DATA_MASK
                result += dataBits shl significantBitsRead

                significantBitsRead += DATA_BITS_PER_BYTE
            } while (nextByte and CONTINUE_MASK > 0)
            return result.toInt()
        }

        override fun encode(data: Int): ByteBuffer {
            var bitStack = data.toLong() and 0xffffffffL
            val buffer = mutableListOf<Byte>()
            do {
                var nextByte = bitStack and DATA_MASK
                bitStack = bitStack shr 7
                if (bitStack != 0L) nextByte = nextByte or CONTINUE_MASK
                buffer.add(nextByte.toByte())
            } while (bitStack != 0L)
            return ByteBuffer.wrap(buffer.toByteArray()).asReadOnlyBuffer()
        }
    }

    val String = object : FieldInterface<String> {
        override fun parse(buffer: ByteBuffer): String {
            val length = VarInt.parse(buffer)
            return (0..<length).map { buffer.getChar() }.joinToString("")
        }

        override fun encode(data: String): ByteBuffer {
            val string = ByteBuffer.wrap(data.toByteArray(Charsets.UTF_8))
            val length = VarInt.encode(string.limit())
            val result = ByteBuffer.allocate(length.limit() + string.limit())
            return result.put(length).put(string).asReadOnlyBuffer().position(0)
        }
    }

    private inline fun <reified U, S> numberFieldCreator(
        crossinline getS: ByteBuffer.() -> S,
        crossinline putS: ByteBuffer.(S) -> ByteBuffer,
        crossinline toU: S.() -> U,
        crossinline fromU: U.() -> S
    ): FieldInterface<U> {
        return object : FieldInterface<U> {
            override fun parse(buffer: ByteBuffer): U = buffer.getS().toU()
            override fun encode(data: U): ByteBuffer {
                val size = U::class.companionObject!!.members.find { it.name == "SIZE_BYTES" }!!.call() as Int
                return ByteBuffer.allocate(size).putS(data.fromU())
            }
        }
    }

    val UnsignedByte = numberFieldCreator(ByteBuffer::get, ByteBuffer::put, Byte::toUByte, UByte::toByte)
    val UnsignedShort = numberFieldCreator(ByteBuffer::getShort, ByteBuffer::putShort, Short::toUShort, UShort::toShort)
    val UnsignedLong = numberFieldCreator(ByteBuffer::getLong, ByteBuffer::putLong, Long::toULong, ULong::toLong)
    val SignedLong = numberFieldCreator(ByteBuffer::getLong, ByteBuffer::putLong, Long::toLong, Long::toLong)

    val Json = JsonField
}