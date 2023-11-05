package com.nolanbarry.gateway.delegates.local

import com.nolanbarry.gateway.delegates.ServerDelegate

class LocalDelegate(
    private val directory: String,
    private val localServerPort: Int = 25565
) : ServerDelegate() {
    private var serverProcess: Process? = null
    private var currentState: ServerStatus = ServerStatus.STOPPED

    override suspend fun getCurrentState(): ServerStatus = currentState

    override suspend fun startServer() {
        if (currentState != ServerStatus.STOPPED)
            throw IllegalStateException("Requested server start while state is $currentState")
    }

    override suspend fun stopServer() {
        if (currentState != ServerStatus.STARTED)
            throw IllegalStateException("Requested server stop while state is $currentState")
    }

    override suspend fun getServerAddress(): Pair<String, Int> {
        return "localhost" to localServerPort
    }

}