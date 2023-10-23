package com.nolanbarry.gateway.delegates.local

import com.nolanbarry.gateway.delegates.ServerDelegate

class LocalDelegate : ServerDelegate() {
    private var serverProcess: Process? = null

    override suspend fun getCurrentState(): ServerStatus {
        TODO("Not yet implemented")
    }

    override suspend fun startServer() {
        TODO("Not yet implemented")
    }

    override suspend fun stopServer() {
        TODO("Not yet implemented")
    }

    override suspend fun getServerAddress(): String {
        TODO("Not yet implemented")
    }

}