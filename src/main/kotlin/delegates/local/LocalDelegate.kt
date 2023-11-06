package com.nolanbarry.gateway.delegates.local

import com.nolanbarry.gateway.delegates.ServerDelegate
import com.nolanbarry.gateway.model.IncompatibleServerStateException
import com.nolanbarry.gateway.utils.asFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class LocalDelegate(
    private val directory: Path,
    private val localServerPort: Int = 25566
) : ServerDelegate() {

    companion object {
        private const val EULA_TEXT =
            "You need to agree to the EULA in order to run the server. Go to eula.txt for more info."
        private const val SERVER_STARTING = "Starting minecraft server version"
        private val MAXIMUM_WAIT_FOR_STOP = 10.toDuration(DurationUnit.SECONDS)
    }

    private val jarPath: Path = run {
        val jar = directory.resolve("server.jar")
        if (!jar.toFile().exists()) throw IllegalStateException("No server.jar found in $directory")
        jar
    }

    private var serverProcess: Process? = null

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
            "-jar", jarPath.toString(),
            "--nogui",
            "--port", localServerPort.toString())
        processBuilder.directory(directory.toFile())
        serverProcess = withContext(Dispatchers.IO) { processBuilder.start() }

        val output = serverProcess
            ?.inputStream
            ?.bufferedReader()
            ?.asFlow()
            ?.transform { log.info { it }; emit(it) }
            ?.firstOrNull() { listOf(EULA_TEXT, SERVER_STARTING).any(it::contains) }
            ?: throw IncompatibleServerStateException("Server startup failed")

        if (EULA_TEXT in output) throw IncompatibleServerStateException(EULA_TEXT)

        log.info { "Local server started!" }
    }

    override suspend fun stopServer() = withContext(Dispatchers.IO) {
        // Create local copy so that null-safety can be assumed for remainder of function
        val serverProcess = serverProcess
        if (serverProcess?.isAlive != true) throw IncompatibleServerStateException("Server is not running")

        serverProcess.destroy()
        withTimeoutOrNull(MAXIMUM_WAIT_FOR_STOP.inWholeMilliseconds) {
            serverProcess.waitFor()
        } ?: serverProcess.destroyForcibly().waitFor()

        Unit
    }

    override suspend fun getServerAddress(): Pair<String, Int> {
        return "localhost" to localServerPort
    }

}