package com.nolanbarry.gateway.delegates

import com.nolanbarry.gateway.config.Configuration
import com.nolanbarry.gateway.delegates.ServerDelegate.ServerStatus.*
import com.nolanbarry.gateway.model.MisconfigurationException
import com.nolanbarry.gateway.model.SOCKET_SELECTOR
import com.nolanbarry.gateway.model.ServerState
import com.nolanbarry.gateway.utils.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.Clock
import kotlin.reflect.full.primaryConstructor
import kotlin.time.Duration

abstract class ServerDelegate {
    protected val config = Configuration.gateway

    protected var timeEmpty = Duration.ZERO
    protected var playerCount = 0
    protected var lastCheckup = Clock.System.now()
    protected val log = getLogger {}

    protected val stateTransition = Mutex()
    private var state = UNKNOWN

    companion object {
        /** TODO: Load this from gateway configuration or elsewhere defined at compile-time */
        private const val DELEGATE_PROPERTIES_PATH = "/local/delegate.properties"

        /** Retrieve the ServerDelegate implementation chosen at build time.
         * @throws IllegalArgumentException If a programming or internal configuration error occurred
         * @throws MisconfigurationException If a properties file is misconfigured */
        fun load(): ServerDelegate {
            val properties = ResourceLoader.loadProperties(DELEGATE_PROPERTIES_PATH)
            val packageName = properties.retrieve("package")

            val log = getLogger {}

            val delegatesPackage = this::class.java.packageName
            val delegateClasspath = "$delegatesPackage.$packageName.${packageName.capitalize()}Delegate"
            val propertiesClasspath = "$delegatesPackage.$packageName.${packageName.capitalize()}Config"

            log.debug { "Building server delegate:" }
            log.debug { "Configured package: $packageName" }

            try {
                log.debug { "Looking for properties implementation at $propertiesClasspath" }
                val propertiesClass = Class.forName(propertiesClasspath).kotlin
                val config = properties.loadInto(propertiesClass)

                log.debug { "Looking for delegate implementation at $delegateClasspath" }
                val delegateClass = Class.forName(delegateClasspath).kotlin

                val constructor = delegateClass.primaryConstructor
                    ?: throw IllegalArgumentException(CLASS_HAS_NO_PRIMARY_CONSTRUCTOR(delegateClass))
                val configParameter = constructor.parameters.find { param -> param.type == propertiesClass }
                    ?: throw IllegalArgumentException(CLASS_MUST_ACCEPT_TYPE(delegateClass, propertiesClass))

                return delegateClass.primaryConstructor!!.callBy(mapOf(configParameter to config)) as ServerDelegate
            } catch (e: Exception) {
                log.error(e) { "Server delegate injection failed:" }
                throw e
            }
        }
    }

    protected abstract suspend fun getCurrentState(): ServerStatus
    protected abstract suspend fun startServer()
    protected abstract suspend fun stopServer()
    abstract suspend fun getServerAddress(): String

    /** Where the server currently is in its availability lifecycle. The server starts with status [UNKNOWN], this is
     * updated the next time status is prompted into a specific state (see
     * [waitForServerToBe(ServerStatus)][waitForServerToBe]).
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
        stateTransition.lock()

        val pendingStateUpdateChannel = Channel<Unit>(Channel.CONFLATED)

        while (desiredState != state) {
            when (state) {
                UNKNOWN -> state = getCurrentState()
                STARTING,
                STOPPING -> pendingStateUpdateChannel.receive()

                STARTED -> {
                    state = STOPPING
                    launch {
                        stopServer()
                        pendingStateUpdateChannel.send(Unit)
                        state = STOPPED
                    }
                }

                STOPPED -> {
                    state = STARTING
                    launch {
                        startServer()
                        pendingStateUpdateChannel.send(Unit)
                        state = STARTED
                    }
                }
            }
        }

        stateTransition.unlock()
    }

    suspend fun openSocket(): Socket = coroutineScope {
        waitForServerToBe(STARTED)
        val address = getServerAddress()
        val socket = aSocket(SOCKET_SELECTOR).tcp().connect(address, config.port)
        socket
    }

    /** Retrieve the server state, which is a JSON object containing information about the server, such as number of
     * players online, message of the day, version, etc. Distinct from server *status*: see [ServerStatus] */
    private suspend fun getState(): ServerState = coroutineScope {
        TODO()
    }

    /** Update internal knowledge of server state (player count) and stop server `config.timeout` time has elapsed
     * with no players. */
    private suspend fun checkup() = coroutineScope {
        runCatching {
            if (state == STOPPED) {
                playerCount = 0
                timeEmpty = Duration.ZERO
                return@coroutineScope
            }

            val status = getState()
            playerCount = status.players.online
            val now = Clock.System.now()
            if (playerCount == 0) {
                timeEmpty += lastCheckup - now

                if (timeEmpty >= config.timeout) {
                    timeEmpty = Duration.ZERO
                    waitForServerToBe(STOPPED)
                }
            } else timeEmpty = Duration.ZERO
            lastCheckup = now
        }.onFailure { exception ->
            log.error(exception) { "Server checkup failed." }
        }
    }

    /** A job that loops forever, checking whether the server should be closed regularly as defined in `config
     * .frequency`. This function will not return until cancelled. */
    private suspend fun monitor() = coroutineScope {
        val metronome = createMetronome(config.frequency)
        metronome.collect { checkup() }
    }
}