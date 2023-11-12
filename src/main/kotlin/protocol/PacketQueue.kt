package com.nolanbarry.gateway.protocol

import com.nolanbarry.gateway.model.ConnectionClosedException
import com.nolanbarry.gateway.protocol.packet.RawPacket
import io.ktor.utils.io.*
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class PacketQueue(private val readChannel: ByteReadChannel) {

    companion object {
        private const val MINIMUM_BUFFER_SIZE = 256
        private const val GROW_THRESHOLD = 0.75
        private const val SHRINK_THRESHOLD = 0.25
        private val TIMEOUT = 3.toDuration(DurationUnit.SECONDS)
    }

    private var buffer = ByteBuffer.allocate(MINIMUM_BUFFER_SIZE)
    private val bufferFill get() = buffer.position() / buffer.capacity().toDouble()
    private var closed = false

    fun end(): Pair<ByteBuffer, ByteReadChannel> {
        if (closed) throw RuntimeException("The packet queue is closed.")
        closed = true
        return buffer to readChannel
    }

    /** Suspend until the next `RawPacket` is available and return it */
    suspend fun consume(): RawPacket {
        if (closed) throw RuntimeException("The packet queue is closed.")
        do {
            val packet = RawPacket.from(buffer)

            if (packet != null) {
                shrinkIfLow()
                return packet
            }

            if (readChannel.isClosedForRead) throw ConnectionClosedException()

            growIfFilled()
            withTimeout(TIMEOUT) { readChannel.readAvailable(buffer) }
        } while (true)
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
        buffer.limit(buffer.position())
        buffer.position(0)
        smallerBuffer.put(buffer)
        buffer = smallerBuffer
    }
}