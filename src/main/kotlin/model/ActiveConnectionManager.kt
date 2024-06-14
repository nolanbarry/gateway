package com.nolanbarry.gateway.model

import com.nolanbarry.gateway.utils.getLogger
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.net.SocketException
import kotlin.random.Random

/** Once a client logs in, its connection to the server is managed here. This class forwards bytes between the two
 * connections and safely handles closing both on exception or connection end. */
object ActiveConnectionManager {
    private val log = getLogger {}
    private val scope = CoroutineScope(Dispatchers.Default)
    private val clients = mutableMapOf<String, ActiveConnection>()

    data class ReadWriteChannel(val read: ByteReadChannel, val write: ByteWriteChannel)
    data class ActiveConnection(val client: ReadWriteChannel, val server: ReadWriteChannel) {
        val id = Random.Default
            .nextBytes(32)
            .joinToString("") { byte -> byte.toUByte().toString(16) }
        val shortId = id.slice(0..6)

        private fun CoroutineScope.pipe(from: ByteReadChannel, to: ByteWriteChannel) = launch {
            try {
                while (true) to.writeByte(from.readByte())
            } finally {
                to.close()
            }
        }

        suspend fun join() {
            try {
                coroutineScope {
                    pipe(from = client.read, to = server.write)
                    pipe(from = server.read, to = client.write)
                }
            } catch (e: Throwable) {
                when (e) {
                    is ClosedWriteChannelException,
                    is ClosedReceiveChannelException,
                    is SocketException -> Unit
                    else -> log.debug(e) { "Connection '$shortId' ended exceptionally" }
                }
            } finally {
                log.debug { "Long-lasting connection '$shortId' closed" }
                clients.remove(id)
            }
        }
    }


    fun register(client: Pair<ByteReadChannel, ByteWriteChannel>, server: Pair<ByteReadChannel, ByteWriteChannel>) {
        val connection = ActiveConnection(
            ReadWriteChannel(client.first, client.second),
            ReadWriteChannel(server.first, server.second)
        )

        log.debug { "Client '${connection.shortId}' registered for long-lasted connection" }

        clients[connection.id] = connection
        scope.launch { connection.join() }
    }
}