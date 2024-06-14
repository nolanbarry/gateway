package com.nolanbarry.gateway.delegates

import com.nolanbarry.gateway.config.BaseConfiguration
import com.nolanbarry.gateway.config.ConfigurationSource
import com.nolanbarry.gateway.delegates.ServerDelegate.ServerStatus.*
import com.nolanbarry.gateway.model.*
import com.nolanbarry.gateway.protocol.Exchange
import com.nolanbarry.gateway.protocol.PacketQueue
import com.nolanbarry.gateway.protocol.packet.Client
import com.nolanbarry.gateway.protocol.packet.Packet
import com.nolanbarry.gateway.protocol.packet.Server
import com.nolanbarry.gateway.utils.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(DelicateCoroutinesApi::class)
abstract class ServerDelegate(val configuration: BaseConfiguration) : ConfigurationSource by configuration {

    protected var timeEmpty = Duration.ZERO
    protected var playerCount = 0
    protected var lastCheckup = Clock.System.now()
    protected val log = getLogger {}

    protected val stateTransition = Mutex()
    var state = UNKNOWN
        private set


    init {
        GlobalScope.launch { monitor() }
    }

    companion object {
        private val BACKOFF = 3.seconds
        private val DEFAULT_SOCKET_TIMEOUT = 3.seconds
        private val SERVER_TRANSITION_TIMEOUT = 60.seconds
        private val SERVER_TRANSITION_PING_FREQUENCY = 1.seconds
        private const val MAX_SERVER_ACTION_ATTEMPTS = 5

        /** Retrieve the ServerDelegate implementation chosen at build time.
         * @throws IllegalArgumentException If a programming or internal configuration error occurred
         * @throws MisconfigurationException If a properties file is misconfigured */
        fun load(configuration: BaseConfiguration): ServerDelegate {
            val delegateName = configuration.delegate
            val log = getLogger {}

            val delegatesPackage = this::class.java.packageName
            val delegateClasspath = "$delegatesPackage.$delegateName.${delegateName.capitalize()}Delegate"

            log.debug { "Building server delegate:" }
            log.debug { "Configured package: $delegateName" }

            try {
                log.debug { "Looking for delegate implementation at $delegateClasspath" }
                val delegateClass = Class.forName(delegateClasspath).kotlin

                if (!delegateClass.isSubclassOf(ServerDelegate::class))
                    throw IllegalArgumentException(CLASS_MUST_BE_SUBTYPE_OF(ServerDelegate::class, delegateClass))

                // Find constructor accepting only a GatewayConfiguration as an argument
                val constructor = delegateClass.constructors.firstOrNull {
                    it.parameters.size == 1 &&
                            (it.parameters.first().type == BaseConfiguration::class.createType())
                } ?: throw IllegalStateException("No valid constructors exist for class ${delegateClass.simpleName}")

                return constructor.call(configuration) as ServerDelegate
            } catch (e: Exception) {
                log.error { "Server delegate injection failed:" }
                throw e
            }
        }
    }

    /** Retrieve the status of the minecraft server *from its source*. This might involve pinging both the cloud
     * provider to make sure the VM is online, and the server itself to confirm that it's accepting connect ions.
     * Implementations of [getCurrentStatus] are only required to return [STOPPED] or [STARTED], and [STARTED] should
     * only be returned the server is accepting player connections (otherwise [STOPPED] is acceptable). */
    protected abstract suspend fun getCurrentStatus(): ServerStatus

    /** Start the server. Throwing an [IncompatibleServerStateException] will cause the abstract class to back off, and
     *  can be thrown if the server can't be started for any reason. If there is no sense in retrying, throw an
     *  [UnrecoverableServerException] and the requesting client connection will be closed. Return only after
     *  [getCurrentStatus] will return [STARTED] or [STARTING]. */
    protected abstract suspend fun startServer()

    /** Stop the server. Throwing an [IncompatibleServerStateException] will cause the abstract class to back off, and
     *  can be thrown if the server can't be stopped for any reason. If there is no sense in retrying, throw an
     *  [UnrecoverableServerException]. Return only after [getCurrentStatus] will return [STOPPED] or [STOPPING]. */
    protected abstract suspend fun stopServer()

    /** Retrieve the address and port of the minecraft server. Throws [IncompatibleServerStateException] if the
     * server is unavailable. */
    abstract suspend fun getServerAddress(): Pair<String, Int>

    /** Where the server currently is in its availability lifecycle. The server starts with status [UNKNOWN], this is
     * updated the next time status is prompted into a specific state (see [waitForServerToBe]).
     *
     * Distinct from server *status*: see [getState()][getState]. */
    enum class ServerStatus { STARTING, STARTED, STOPPING, STOPPED, UNKNOWN }

    /** Suspend until the server has reached the desired state. If necessary, this function will take action to
     * transition the server into the desired state.
     * @param desiredState The state to wait for. Cannot be [UNKNOWN]
     */
    suspend fun waitForServerToBe(desiredState: ServerStatus) = coroutineScope {
        if (desiredState == UNKNOWN) throw IllegalArgumentException("UNKNOWN is not a valid desired state")

        // Locking for the duration of this entire function is conservative, but easy. Realistically there shouldn't
        // be a need for separate routines to be waiting on opposing states, and even if there was, this is one valid
        // solution to resolving that conflict.
        stateTransition.withLock {

            var serverActionAttempts = 0

            while (desiredState != state) {
                // Every state has a single "next" state, so we just cycle through them until we reach the desired state.
                when (state) {
                    UNKNOWN -> state = getCurrentStatus()
                    STARTING,
                    STOPPING -> withTimeout(SERVER_TRANSITION_TIMEOUT) {
                        state = getCurrentStatus()
                        while (state in listOf(STARTING, STOPPING)) {
                            delay(SERVER_TRANSITION_PING_FREQUENCY)
                            state = getCurrentStatus()
                        }
                    }

                    STOPPED,
                    STARTED -> {
                        // Put the server in the next, intermediate state, then actually start/stop server asynchronously,
                        if (serverActionAttempts >= MAX_SERVER_ACTION_ATTEMPTS) {
                            val attemptedAction = if (state == STOPPED) "start" else "stop"
                            throw UnrecoverableServerException(
                                FAILED_TO_DO_AFTER_X_ATTEMPTS(attemptedAction, MAX_SERVER_ACTION_ATTEMPTS))
                        }
                        serverActionAttempts++
                        state = if (state == STOPPED) STARTING else STOPPING
                        launch {
                            try {
                                if (state == STOPPING) stopServer() else startServer()
                            } catch (e: IncompatibleServerStateException) {
                                delay(BACKOFF)
                                state = UNKNOWN
                            }
                        }
                    }
                }
            }

        }
    }

    suspend fun openSocket() = coroutineScope {
        waitForServerToBe(STARTED)
        val (address, port) = getServerAddress()
        val socket = aSocket(SOCKET_SELECTOR).tcp().connect(address, port)
        Triple(socket, address, port)
    }

    /** Retrieve the server state, which is a JSON object containing information about the server, such as number of
     * players online, message of the day, version, etc. Distinct from server *status*: see [ServerStatus] */
    private suspend fun getState(): ServerState = coroutineScope {
        val (socket, address, port) = openSocket()
        socket.use {
            val packetQueue = PacketQueue(socket.openReadChannel())
            val toServer = socket.openWriteChannel(autoFlush = true)
            val handshake = Packet(
                0,
                Client.Handshake(
                    protocolVersion = configuration.protocol.toInt(),
                    serverAddress = address,
                    serverPort = port.toUShort(),
                    nextState = Exchange.State.STATUS_REQUEST.ordinal)
            ).encode()
            val statusRequest = Packet(0, Client.StatusRequest()).encode()

            toServer.writeFully(handshake)
            toServer.writeFully(statusRequest)

            val response = packetQueue.consume().interpretAs<Server.StatusResponse>()
            response.payload.response
        }
    }

    /** Update internal knowledge of server state (player count) and stop server if `config.timeout` time has elapsed
     * with no players. */
    private suspend fun checkup(): Unit = coroutineScope {
        log.debug { "Performing server checkup, current state is $state" }
        runCatching {
            when (state) {
                UNKNOWN -> {
                    log.debug { "Attempting to retrieve updated state" }
                    state = getCurrentStatus()
                    if (state == UNKNOWN) {
                        log.debug { "Nothing changed, state is still $state" }
                        return@runCatching
                    }
                    log.debug { "Server state updated to $state. Rerunning checkup" }
                    checkup()
                }

                STOPPED, STOPPING, STARTING -> {
                    log.debug { "Nothing to check" }
                    playerCount = 0
                    timeEmpty = Duration.ZERO
                }

                STARTED -> {
                    try {
                        val status = getState()
                        playerCount = status.players.online
                        val now = Clock.System.now()
                        log.debug { "Player count: $playerCount" }
                        if (playerCount == 0) {
                            timeEmpty += now - lastCheckup
                            log.debug { "Time empty: $timeEmpty" }
                            if (timeEmpty >= configuration.timeout) {
                                log.debug { "Server has been empty for greater than ${configuration.timeout}" }
                                log.debug { "Attempting to shut down" }
                                timeEmpty = Duration.ZERO
                                waitForServerToBe(STOPPED)
                                log.debug { "Server stopped" }
                            }
                        } else timeEmpty = Duration.ZERO
                        lastCheckup = now
                    } catch (e: Exception) {
                        log.warn {
                            "Server checkup failed - is the server really started? Putting state into UNKNOWN and" +
                                    "rerunning checkup"
                        }
                        state = UNKNOWN
                        checkup()
                    }
                }
            }


        }.onFailure { exception ->
            log.error(exception) { "Server checkup failed." }
        }
    }

    /** A job that loops forever, checking whether the server should be closed regularly as defined in `config.frequency`.
     * This function will not return until cancelled. */
    private suspend fun monitor() = coroutineScope {
        val metronome = createMetronome(configuration.frequency)
        metronome.collect { checkup() }
    }

    /** TCP ping on [address]:[port]. Returns `true` if connection was established within [timeout]. */
    protected suspend fun isAcceptingConnections(
        address: String,
        port: Int,
        timeout: Duration = DEFAULT_SOCKET_TIMEOUT
    ): Boolean {
        val attempt = runCatching {
            val builder = aSocket(SOCKET_SELECTOR).tcp()
            log.debug { "Checking if $address:$port is accepting connections" }
            withTimeout(timeout.inWholeMilliseconds) { builder.connect(address, port).close() }
        }
        log.debug { "Connection to $address:$port ${if (attempt.isSuccess) "succeeded" else "failed"}" }
        return attempt.isSuccess
    }
}