package com.nolanbarry.gateway

import com.nolanbarry.gateway.config.Configuration
import com.nolanbarry.gateway.delegates.ServerDelegateInjector
import com.nolanbarry.gateway.model.SOCKET_SELECTOR
import com.nolanbarry.gateway.protocol.Exchange
import com.nolanbarry.gateway.utils.getLogger
import io.ktor.network.sockets.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

suspend fun main(args: Array<String>) = coroutineScope {

    val log = getLogger {}

    log.debug { "Initializing gateway configuration" }
    Configuration.init(args)

    val config = Configuration.gateway

    val serverDelegate = ServerDelegateInjector.get()

    log.info { "Starting gateway on port ${config.port}" }
    val socket = aSocket(SOCKET_SELECTOR).tcp().bind("0.0.0.0", config.port)

    while (true) {
        val client = socket.accept()
        launch { Exchange(serverDelegate, client).handle() }
    }
}