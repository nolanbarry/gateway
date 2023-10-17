package com.nolanbarry.gateway.protocol

import com.nolanbarry.gateway.protocol.packet.RawPacket
import io.ktor.utils.io.*
import java.nio.ByteBuffer

class PacketQueue(private val readChannel: ByteReadChannel) {

    companion object {
        private const val MINIMUM_BUFFER_SIZE = 256
        private const val GROW_THRESHOLD = 0.75
        private const val SHRINK_THRESHOLD = 0.25
    }

    private var buffer = ByteBuffer.allocate(MINIMUM_BUFFER_SIZE)
    private val bufferFill get() = buffer.limit() / buffer.capacity().toDouble()
    private var closed = false

    fun end(): Pair<ByteBuffer, ByteReadChannel> {
        if (closed) throw RuntimeException("The packet queue is closed.")
        closed = true
        return buffer to readChannel
    }

    /** Suspend until the next `RawPacket` is available and return it */
    suspend fun consume(): RawPacket {
        if (closed) throw RuntimeException("The packet queue is closed.")
        while (true) {
            growIfFilled()

            readChannel.readAvailable(buffer)
            val packet = RawPacket.from(buffer) ?: continue

            buffer.compact()
            shrinkIfLow()

            return packet
        }
    }

    /** Check if `buffer` has exceeded `GROW_THRESHOLD` fill level; if it has, double its capacity. */
    private fun growIfFilled() {
        if (bufferFill < GROW_THRESHOLD) return

        val biggerBuffer = ByteBuffer.allocate(buffer.capacity() * 2)
        biggerBuffer.put(buffer)
        buffer = biggerBuffer
    }

    /** Check if `buffer` has fallen below `SHRINK_THRESHOLD` fill level; if it has, halve its capacity. */
    private fun shrinkIfLow() {
        assert(SHRINK_THRESHOLD < 0.5)
        if (bufferFill > SHRINK_THRESHOLD) return

        val smallerBuffer = ByteBuffer.allocate(buffer.capacity() / 2)
        smallerBuffer.put(buffer)
        buffer = smallerBuffer
    }
}