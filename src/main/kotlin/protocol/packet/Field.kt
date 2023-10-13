package com.nolanbarry.gateway.protocol.packet

import com.nolanbarry.gateway.model.*
import java.lang.IllegalArgumentException
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject

data class Offset(var value: Int = 0)

interface FieldInterface<T> {
    fun parse(buffer: List<Byte>, offset: Offset): T
    fun encode(data: T): List<Byte>
}

object Field {
    private const val DATA_BITS_PER_BYTE = 7
    private const val BYTE_MASK: Long = 0xff
    private const val DATA_MASK: Long = BYTE_MASK ushr (Byte.SIZE_BITS - DATA_BITS_PER_BYTE)
    private const val CONTINUE_MASK: Long = BYTE_MASK shl DATA_BITS_PER_BYTE and BYTE_MASK

    val VarInt = object : FieldInterface<Int> {
        override fun parse(buffer: List<Byte>, offset: Offset): Int {
            var result = 0L
            var significantBitsRead = 0
            do {
                if (significantBitsRead >= 32) throw InvalidDataException("VarInt is too big")
                if (offset.value > buffer.size) throw IncompleteBufferException()

                val nextByte = buffer[offset.value].toLong()
                val dataBits = nextByte and DATA_MASK
                result += dataBits shl significantBitsRead
                offset.value++

                significantBitsRead += DATA_BITS_PER_BYTE
            } while (nextByte and CONTINUE_MASK > 0)
            return result.toInt()
        }

        override fun encode(data: Int): List<Byte> {
            var bitStack = data.toLong() and 0xffffffffL
            val buffer = mutableListOf<Byte>()
            do {
                var nextByte = bitStack and DATA_MASK
                bitStack = bitStack shr 7
                if (bitStack != 0L) nextByte = nextByte or CONTINUE_MASK
                buffer.add(nextByte.toByte())
            } while (bitStack != 0L)
            return buffer
        }
    }

    val String = object : FieldInterface<String> {
        override fun parse(buffer: List<Byte>, offset: Offset): String {
            val length = VarInt.parse(buffer, offset)
            if (offset.value + length > buffer.size) throw IncompleteBufferException()
            return buffer.slice(offset.value..<offset.value + length)
                .joinToString { byte -> byte.toUInt().toInt().toChar().toString() }
        }

        override fun encode(data: String): List<Byte> {
            val byteArray = data.toByteArray(Charsets.UTF_8).toList()
            val length = byteArray.size
            return VarInt.encode(length) + byteArray
        }
    }

    private fun <T : Any> numberFieldCreator(
        n: KClass<T>,
        toULong: T.() -> ULong,
        fromULong: ULong.() -> T
    ): FieldInterface<T> {
        val bytes = n.companionObject?.members?.find { it.name == "SIZE_BYTES" }?.call() as Int?
            ?: throw IllegalArgumentException("Could not find SIZE_BYTES for $n")
        return object : FieldInterface<T> {
            override fun parse(buffer: List<Byte>, offset: Offset): T {
                val result = buffer.slice(offset.value..<offset.value + bytes)
                    .asReversed()
                    .mapIndexed { i, byte -> byte.toULong() shl (Byte.SIZE_BITS * i) }
                    .sum()
                return result.fromULong()
            }

            override fun encode(data: T): List<Byte> {
                val ulong = data.toULong()
                return List(bytes) { i -> (ulong shr (Byte.SIZE_BITS * i)).toByte() }
                    .asReversed()
            }
        }
    }

    val UnsignedByte = numberFieldCreator(UByte::class, UByte::toULong, ULong::toUByte)
    val UnsignedShort = numberFieldCreator(UShort::class, UShort::toULong, ULong::toUShort)
    val UnsignedLong = numberFieldCreator(ULong::class, ULong::toULong, ULong::toULong)
    val Long = numberFieldCreator(kotlin.Long::class, kotlin.Long::toULong, ULong::toLong)
}