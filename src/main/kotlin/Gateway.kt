package com.nolanbarry.gateway

import com.nolanbarry.gateway.config.GatewayConfiguration
import com.nolanbarry.gateway.delegates.ServerDelegate
import com.nolanbarry.gateway.model.SOCKET_SELECTOR
import com.nolanbarry.gateway.protocol.Exchange
import com.nolanbarry.gateway.utils.getLogger
import io.ktor.network.sockets.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

suspend fun main(args: Array<String>) = coroutineScope {

    val log = getLogger {}

    log.debug { "Initializing gateway configuration" }
    GatewayConfiguration.init(args)

    val serverDelegate = ServerDelegate.load()

    log.info { "Starting gateway on port ${GatewayConfiguration.port}" }
    val socket = aSocket(SOCKET_SELECTOR).tcp().bind("0.0.0.0", GatewayConfiguration.port)

    while (true) {
        val client = socket.accept()
        launch { Exchange(serverDelegate, client).handle() }
    }
}