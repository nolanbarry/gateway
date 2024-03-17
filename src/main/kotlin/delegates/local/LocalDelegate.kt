package com.nolanbarry.gateway.delegates.local

import com.nolanbarry.gateway.delegates.ServerDelegate
import com.nolanbarry.gateway.model.IncompatibleServerStateException
import com.nolanbarry.gateway.model.UnrecoverableServerException
import com.nolanbarry.gateway.utils.asFlow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

class LocalDelegate(
    private val directory: Path,
    private val localServerPort: Int = 48291
) : ServerDelegate() {

    companion object {
        private const val EULA_TEXT = "You need to agree to the EULA in order to run the server."
        private const val SERVER_STARTING = "Starting minecraft"
        private const val LINGERING_SERVER = "session.lock: already locked (possibly by other Minecraft instance?)"
        private val MAXIMUM_WAIT_FOR_STOP = 10.seconds
        private val TIMEOUT = 25.seconds
    }

    init {
        val jar = directory.resolve("server.jar")
        if (!jar.toFile().exists()) throw IllegalStateException("No server.jar found in $directory")
    }

    private var serverProcess: Process? = null

    init {
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                log.info { "Stopping Minecraft server" }
                serverProcess?.destroy()
            }
        })
    }

    override suspend fun getCurrentState(): ServerStatus {
        val serverProcess = serverProcess
        return when {
            serverProcess?.isAlive != true -> ServerStatus.STOPPED
            else -> ServerStatus.STARTED
        }
    }

    override suspend fun startServer() {

        log.info { "Starting local Minecraft server on port $localServerPort" }

        val processBuilder = ProcessBuilder(
            "java",
            "-jar", "server.jar",
            "--nogui",
            "--port", localServerPort.toString())
            .directory(directory.toFile())
        serverProcess = withContext(Dispatchers.IO) { processBuilder.start() }

        val output = serverProcess
            ?.inputStream
            ?.bufferedReader()
            ?.asFlow()
            ?.transform { log.info { it }; emit(it) }
            ?.firstOrNull { listOf(EULA_TEXT, SERVER_STARTING, LINGERING_SERVER).any(it::contains) }
            ?: throw IncompatibleServerStateException("Server startup failed")

        if (LINGERING_SERVER in output) {
            killProcessUsingPort()
            throw IncompatibleServerStateException("Another server was using the port, try again")
        } else if (EULA_TEXT in output) throw UnrecoverableServerException(EULA_TEXT)

        log.info { "Local server started!" }
        log.info { "Waiting for it to be available" }

        withTimeout(TIMEOUT) {
            while (!isAcceptingConnections("localhost", localServerPort))
                delay(500)
        }

        log.info { "Server is available" }
    }

    override suspend fun stopServer() = withContext(Dispatchers.IO) {
        // Create local copy so that null-safety can be assumed for remainder of function
        val serverProcess = serverProcess
        this@LocalDelegate.serverProcess = null

        if (serverProcess?.isAlive != true) throw IncompatibleServerStateException("Server is not running")

        serverProcess.destroy()
        withTimeoutOrNull(MAXIMUM_WAIT_FOR_STOP) {
            serverProcess.waitFor()
        } ?: serverProcess.destroyForcibly().waitFor()

        Unit
    }

    override suspend fun getServerAddress(): Pair<String, Int> {
        return "localhost" to localServerPort
    }

    private fun killProcessUsingPort() = runBlocking {
        log.info { "Killing process using port $localServerPort" }
        val pid = ProcessBuilder("lsof", "-t", "-i:$localServerPort")
            .start().inputStream.bufferedReader().asFlow().toList().firstOrNull()
        if (pid == null) {
            log.debug { "No process is using port $localServerPort" }
        } else {
            log.debug { "Process $pid is using port $localServerPort" }
            ProcessBuilder("kill", pid)
                .start().inputStream.bufferedReader().asFlow().collect { log.info { it } }
        }

    }

}