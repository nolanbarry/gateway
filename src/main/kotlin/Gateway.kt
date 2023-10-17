package com.nolanbarry.gateway

import com.nolanbarry.gateway.model.Configuration
import com.nolanbarry.gateway.model.SOCKET_SELECTOR
import com.nolanbarry.gateway.protocol.Exchange
import com.nolanbarry.gateway.delegates.ServerDelegate
import io.ktor.network.sockets.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

suspend fun main(args: Array<String>) = coroutineScope {
    val config = Configuration.fromCommandLine(args)
    println("Starting gateway on port ${config.gatewayPort}")

    val socket = aSocket(SOCKET_SELECTOR).tcp().bind("0.0.0.0", config.gatewayPort)
    val serverDelegate = getServerDelegate()
    while (true) {
        val client = socket.accept()
        launch { Exchange(serverDelegate, client) }
    }
}

fun getServerDelegate(): ServerDelegate {
    TODO()
}