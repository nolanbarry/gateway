package com.nolanbarry.gateway.protocol

import io.ktor.utils.io.*

class PacketQueue(private val readChannel: ByteReadChannel) {

    val buffer = ArrayDeque<Byte>()

    fun consume() {
        readChannel.readAvailable()
    }
}