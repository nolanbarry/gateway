package com.nolanbarry.gateway

import com.nolanbarry.gateway.protocol.Exchange
import com.nolanbarry.gateway.protocol.ServerDelegate
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

suspend fun main(args: Array<String>) = coroutineScope {
    val config = Configuration.fromCommandLine(args)
    println("Starting gateway on port ${config.gatewayPort}")

    val socket = aSocket(SOCKET_SELECTOR).tcp().bind("0.0.0.0", config.gatewayPort)
    val serverDelegate = getServerDelegate()
    while (true) {
        socket.accept()
        launch {
            Exchange(serverDelegate, config)
        }
    }
}

fun getServerDelegate(): ServerDelegate {
    TODO()
}