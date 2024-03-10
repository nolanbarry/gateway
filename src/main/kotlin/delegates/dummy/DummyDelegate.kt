package com.nolanbarry.gateway.delegates.dummy

import com.nolanbarry.gateway.delegates.ServerDelegate
import com.nolanbarry.gateway.model.IncompatibleServerStateException

class DummyDelegate : ServerDelegate() {
    override suspend fun getCurrentState(): ServerStatus {
        return ServerStatus.STOPPED
    }

    override suspend fun startServer() {
        throw IncompatibleServerStateException("Dummy server can't actually be started")
    }

    override suspend fun stopServer() {
        throw IncompatibleServerStateException("Dummy server can't actually be stopped")
    }

    override suspend fun getServerAddress(): Pair<String, Int> {
        return "localhost" to 0
    }

}