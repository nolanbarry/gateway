package com.nolanbarry.gateway.delegates.aws

import aws.sdk.kotlin.services.ec2.Ec2Client
import aws.sdk.kotlin.services.ec2.describeInstances
import aws.sdk.kotlin.services.ec2.startInstances
import aws.sdk.kotlin.services.ec2.stopInstances
import com.nolanbarry.gateway.delegates.ServerDelegate
import com.nolanbarry.gateway.model.IncompatibleServerStateException
import com.nolanbarry.gateway.model.MisconfigurationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

@Suppress("unused")
class AwsDelegate(
    private val instanceId: String,
    private val serverPort: Int,
    awsRegion: String?
) : ServerDelegate() {

    private val region = awsRegion ?: System.getenv()["AWS_DEFAULT_REGION"]
    ?: throw MisconfigurationException("No AWS region specified")
    private val ec2 = Ec2Client { region = this@AwsDelegate.region }

    private object InstanceState {
        const val PENDING = 0
        const val RUNNING = 16
        const val SHUTTING_DOWN = 32
        const val TERMINATED = 48
        const val STOPPING = 64
        const val STOPPED = 80
    }

    private suspend fun getInstanceState() = ec2.describeInstances {
        instanceIds = listOf(instanceId)
    }.reservations?.firstOrNull()?.instances?.firstOrNull()
        ?: throw IllegalStateException("No instance found with id $instanceId")


    override suspend fun getCurrentState(): ServerStatus {
        val description = getInstanceState()

        val instanceStatus = when (description.state?.code) {
            InstanceState.PENDING -> ServerStatus.STARTING
            InstanceState.RUNNING -> ServerStatus.STARTED
            InstanceState.SHUTTING_DOWN -> throw IllegalStateException("Instance $instanceId is terminated")
            InstanceState.TERMINATED -> throw IllegalStateException("Instance $instanceId is terminated")
            InstanceState.STOPPING -> ServerStatus.STOPPING
            InstanceState.STOPPED -> ServerStatus.STOPPED
            else -> ServerStatus.UNKNOWN
        }

        return when (instanceStatus) {
            ServerStatus.STARTED -> {
                val ip = description.privateIpAddress
                    ?: throw IllegalStateException("Instance $instanceId has no IP address")

                val mcServerRunning = isAcceptingConnections(ip, serverPort)
                if (mcServerRunning) {
                    ServerStatus.STARTED
                } else {
                    log.debug { "Instance is started, but MC server is not reachable" }
                    ServerStatus.STARTING
                }
            }

            else -> instanceStatus
        }
    }

    override suspend fun startServer() {
        val initialState = getInstanceState().state?.code
        if (initialState == InstanceState.STOPPED)
            ec2.startInstances { instanceIds = listOf(instanceId) }

        when (initialState) {
            InstanceState.STOPPED,
            InstanceState.RUNNING,
            InstanceState.PENDING -> withTimeoutOrNull(30.seconds) {
                while (getCurrentState() == ServerStatus.STARTING) delay(1.seconds)
                if (getCurrentState() != ServerStatus.STARTED)
                    throw IncompatibleServerStateException("Server startup failed")
            } ?: throw IncompatibleServerStateException("Server took too long to start")

            else -> throw IncompatibleServerStateException("Cannot start server from state $initialState")
        }
    }

    override suspend fun stopServer() {
        val initialState = getInstanceState().state?.code
        if (initialState == InstanceState.RUNNING)
            ec2.stopInstances { instanceIds = listOf(instanceId) }

        when (initialState) {
            InstanceState.RUNNING,
            InstanceState.STOPPING -> withTimeoutOrNull(30.seconds) {
                while (getCurrentState() == ServerStatus.STOPPING) delay(1.seconds)
                if (getCurrentState() != ServerStatus.STOPPED) null else Unit
            } ?: throw IncompatibleServerStateException("Server took too long to stop")

            else -> throw IncompatibleServerStateException("Cannot stop server from state $initialState")
        }
    }

    override suspend fun getServerAddress(): Pair<String, Int> {
        val description = getInstanceState()
        val ip = description.privateIpAddress
            ?: throw IllegalStateException("Instance $instanceId has no IP address")

        return ip to serverPort
    }

}