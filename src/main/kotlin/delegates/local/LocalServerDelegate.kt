package com.nolanbarry.gateway.delegates.local

import com.nolanbarry.gateway.delegates.ServerDelegate

class LocalServerDelegate : ServerDelegate() {
    override suspend fun isStarted(): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun isStopped(): Boolean {
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