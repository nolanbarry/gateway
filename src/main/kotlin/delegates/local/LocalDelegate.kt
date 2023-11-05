package com.nolanbarry.gateway.delegates.local

import com.nolanbarry.gateway.delegates.ServerDelegate

class LocalDelegate(config: LocalConfig) : ServerDelegate() {
    private var serverProcess: Process? = null
    private var currentState: ServerStatus = ServerStatus.STOPPED

    override suspend fun getCurrentState(): ServerStatus = currentState

    override suspend fun startServer() {
        if (currentState != ServerStatus.STOPPED)
            throw IllegalStateException("Requested server start while state is $currentState")
    }

    override suspend fun stopServer() {
        TODO("Not yet implemented")
    }

    override suspend fun getServerAddress(): String {
        TODO("Not yet implemented")
    }

}