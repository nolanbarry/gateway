package com.nolanbarry.gateway.protocol

import com.nolanbarry.gateway.Configuration
import kotlinx.coroutines.coroutineScope

class Exchange(private val serverDelegate: ServerDelegate, private val config: Configuration) {
    enum class State { AWAITING_HANDSHAKE, STATUS_REQUEST, LOGIN, CLOSED }
    private val exchangeState = State.AWAITING_HANDSHAKE

    suspend fun handle() = coroutineScope {
        while (exchangeState != State.CLOSED) {
            val nextPacket = clientPacketQueue.consume()
        }
    }
}
