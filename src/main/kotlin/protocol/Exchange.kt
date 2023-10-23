package com.nolanbarry.gateway.protocol

import com.nolanbarry.gateway.delegates.ServerDelegate
import com.nolanbarry.gateway.utils.getLogger
import com.nolanbarry.gateway.model.*
import com.nolanbarry.gateway.protocol.packet.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random

class Exchange(
    private val serverDelegate: ServerDelegate,
    private val client: Socket
) {
    // STATUS_REQUEST and LOGIN *must* have ordinal values of 1 & 2
    enum class State { AWAITING_HANDSHAKE, STATUS_REQUEST, LOGIN, CLOSED }
    enum class PostPacketAction { CONTINUE, END_MONITORING, END_EXCHANGE }

    private var state = State.AWAITING_HANDSHAKE
    private val exchangeState = State.AWAITING_HANDSHAKE
    private val clientPacketQueue = PacketQueue(client.openReadChannel())
    private val toClient = client.openWriteChannel(autoFlush = true)
    private var server: Socket? = null

    private val exchangeId = Random.nextBytes(6).map { it.toString(16) }
    private val log = getLogger("ex$exchangeId")

    companion object {
        suspend fun pipe(source: ByteReadChannel, dest: ByteWriteChannel) = coroutineScope {
            launch {
                runCatching { while (true) dest.writeByte(source.readByte()) }
            }
        }
    }

    /** Oversee the packet exchange. Returns when the exchange is complete. */
    suspend fun handle() = coroutineScope {
        log.debug { "Connection established" }
        while (exchangeState != State.CLOSED) {
            val nextPacket = clientPacketQueue.consume()

            val handler = when (state) {
                State.AWAITING_HANDSHAKE -> ::handleHandshakePacket
                State.STATUS_REQUEST -> ::handleStatusRequestPacket
                State.LOGIN -> throw IllegalStateException("Packet intercept has ceased")
                State.CLOSED -> throw IllegalStateException("Exchange is closed")
            }

            val actionToTake = handler(nextPacket)

            if (actionToTake == PostPacketAction.END_MONITORING) close()

            when (actionToTake) {
                PostPacketAction.END_EXCHANGE,
                PostPacketAction.END_MONITORING -> break

                PostPacketAction.CONTINUE -> continue
            }
        }
    }

    /** Handle a packet received while state AWAITING_HANDSHAKE */
    private suspend fun handleHandshakePacket(packet: RawPacket): PostPacketAction {
        return when (packet.id) {
            0x00 -> {
                val handshake = packet.interpretAs<Client.Handshake>()
                this.state = when (val nextState = handshake.payload.nextState) {
                    1 -> State.STATUS_REQUEST
                    2 -> State.LOGIN
                    else -> throw InvalidDataException("Invalid next state $nextState")
                }
                if (this.state == State.LOGIN) {
                    initiateLogin()
                    PostPacketAction.END_MONITORING
                } else PostPacketAction.CONTINUE
            }

            0xFE -> {
                // This is a legacy server ping which we should respond to with a canned response indicating we are not
                // a legacy server. See https://wiki.vg/Server_List_Ping#1.6
                toClient.writeFully(LEGACY_SERVER_RESPONSE)
                PostPacketAction.END_EXCHANGE
            }

            else -> throw InvalidDataException("Unexpected handshake packet with id ${packet.fid}")
        }
    }

    /** Handle a packet received while in state STATUS_REQUEST */
    private suspend fun handleStatusRequestPacket(packet: RawPacket): PostPacketAction {
        return when (packet.id) {
            0x00 -> {
                val response = Server.StatusResponse(getStatus())
                toClient.writeFully(Packet(0x00, response).encode())
                PostPacketAction.CONTINUE
            }

            0x01 -> {
                val pingRequest = packet.interpretAs<Client.PingRequest>()
                val pong = Server.PingResponse(pingRequest.payload.pingPayload)
                toClient.writeFully(Packet(0x01, pong).encode())
                PostPacketAction.END_EXCHANGE
            }

            else -> throw InvalidDataException("Unexpected status request packet with id ${packet.fid}")
        }
    }

    /** When client sends a handshake with intent to log in, connect client and server sockets together and back off
     * of packet intercept. Starts the server if it is offline. */
    private suspend fun initiateLogin() {
        log.debug { "Initiating login" }

        val toServer = openServerConnection(State.LOGIN)
        val fromServer = server!!.openReadChannel()

        val (remainingBuffer, fromClient) = clientPacketQueue.end()

        toServer.writeFully(remainingBuffer)

        pipe(fromServer, toClient)
        pipe(fromClient, toServer)
    }

    /** Open a socket to the server, starting the server if it is offline. `this.server` is filled with the socket
     * and will remain not-null for as long as this exchange is active.
     * @param intent The intent to send in the handshake to the server (as `nextState`).
     * @throws IllegalArgumentException If `intent` is not `State.LOGIN` or `State.STATUS_REQUEST`
     */
    private suspend fun openServerConnection(intent: State): ByteWriteChannel {
        log.debug { "Opening connection to server" }
        if (server != null) throw RuntimeException("Server connection already opened")

        val server = serverDelegate.openSocket().also { this.server = it }

        if (intent !in listOf(State.LOGIN, State.STATUS_REQUEST))
            throw IllegalArgumentException("Illegal intent ${intent.name}")

        val handshakePayload = Client.Handshake(
            protocolVersion = Protocol.v1_20_1,
            serverAddress = serverDelegate.getServerAddress(),
            serverPort = serverDelegate.port.toUShort(),
            nextState = intent.ordinal
        )

        val packet = Packet(0x00, handshakePayload).encode()

        val sendChannel = server.openWriteChannel(autoFlush = true)
        sendChannel.writeFully(packet)
        return sendChannel
    }

    /** Get the server status. If the server is online, its response is returned. Otherwise, a default status is
     * returned. */
    private suspend fun getStatus(): ServerState {
        if (serverDelegate.isStarted()) {
            log.debug { "Server is online, forwarding status response from server" }
            val toServer = openServerConnection(State.STATUS_REQUEST)
            val serverPacketQueue = PacketQueue(server!!.openReadChannel())

            toServer.writeFully(Packet(0, Client.StatusRequest()).encode())

            val response = serverPacketQueue.consume().interpretAs<Server.StatusResponse>()

            return response.payload.response
        } else {
            log.debug { "Server is offline, sending default offline status response" }
            return ServerState(
                version = Version(name = "1.20.1 (Gated)", protocol = Protocol.v1_20_1),
                players = Players(max = 0, online = 0),
                description = Chat(text = "Server is offline, connect to start.")
            )
        }
    }

    /** End the exchange by closing client and server sockets. */
    private suspend fun close() = coroutineScope {
        server?.close()
        client.close()
    }
}
