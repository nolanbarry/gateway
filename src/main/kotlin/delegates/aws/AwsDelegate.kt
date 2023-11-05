package com.nolanbarry.gateway.delegates.aws

import com.nolanbarry.gateway.delegates.ServerDelegate

class AwsDelegate : ServerDelegate() {
    override suspend fun getCurrentState(): ServerStatus {
        TODO("Not yet implemented")
    }

    override suspend fun startServer() {
        TODO("Not yet implemented")
    }

    override suspend fun stopServer() {
        TODO("Not yet implemented")
    }

    override suspend fun getServerAddress(): Pair<String, Int> {
        TODO("Not yet implemented")
    }

}