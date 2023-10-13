package com.nolanbarry.gateway.protocol

import com.nolanbarry.gateway.SOCKET_SELECTOR
import com.nolanbarry.gateway.model.ServerStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.sockets.*
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.time.Duration

data class ServerDelegateConfig(
    val port: Int,
    val protocolVersion: String,
    val timeout: Duration,
    val pingInterval: Duration
)

abstract class ServerDelegate(config: ServerDelegateConfig) {
    private val port = config.port
    private val timeout = config.timeout
    private val pingInterval = config.pingInterval
    private val protocolVersion = config.protocolVersion
    private var timeEmpty = Duration.ZERO
    private val stateTransition = Mutex()
    private var playerCount = 0
    private var lastCheckup = Clock.System.now()
    private val log = KotlinLogging.logger {}

    abstract suspend fun isStarted(): Boolean
    abstract suspend fun isStopped(): Boolean
    abstract suspend fun startServer()
    abstract suspend fun stopServer()
    abstract suspend fun getServerAddress(): String

    private enum class ServerState { STARTED, STOPPED }

    private suspend fun waitForServerToBe(desiredState: ServerState) = stateTransition.withLock {
        val started = isStarted()
        val stopped = isStopped()
        if (!started && desiredState == ServerState.STARTED) startServer()
        else if (!stopped && desiredState == ServerState.STOPPED) stopServer()
    }

    private suspend fun openSocket(): Socket = coroutineScope {
        waitForServerToBe(ServerState.STARTED)
        val address = getServerAddress()
        val socket = aSocket(SOCKET_SELECTOR).tcp().connect(address, port)
        socket
    }

    private suspend fun getStatus(): ServerStatus = coroutineScope {
        TODO()
    }

    private suspend fun checkup() = coroutineScope {
        runCatching {
            if (!isStarted()) {
                playerCount = 0
                timeEmpty = Duration.ZERO
                return@coroutineScope
            }

            val status = getStatus()
            playerCount = status.players.online
            val now = Clock.System.now()
            if (playerCount == 0) {
                timeEmpty += lastCheckup - now

                if (timeEmpty >= timeout) {
                    timeEmpty = Duration.ZERO
                    waitForServerToBe(ServerState.STOPPED)
                }
            } else timeEmpty = Duration.ZERO
            lastCheckup = now
        }.onFailure { exception ->
            log.error(exception) { "Server checkup failed." }
        }
    }

    private suspend fun createMetronome() = coroutineScope {
        val metronome = Channel<Unit>(Channel.Factory.CONFLATED)
        launch {
            while (true) {
                delay(pingInterval)
                metronome.trySend(Unit).isClosed || return@launch
            }
        }
        metronome
    }

    private suspend fun monitor() = coroutineScope {
        val metronome = createMetronome()
        async {
            for (tick in metronome) {
                checkup()
            }
        }
    }

}