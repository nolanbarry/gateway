package com.nolanbarry.gateway.config

import com.nolanbarry.gateway.utils.*
import com.nolanbarry.gateway.utils.ResourceLoader.loadProperties
import java.util.Properties
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/** This object loads and encapsulates the `gateway.properties` file, which is the basic configuration file which the
 * user of this program uses to... configure the gateway. These are the options which are available:
 * - `port`: (default: `25565`) The port to host the gateway on. Defaults: `25565`, (Minecraft's default server port)
 * - `protocol`: The protocol to use - this is the protocol number used internally by the Minecraft server protocol.
 * Grab the protocol from https://wiki.vg/Protocol_version_numbers - i.e., The protocol number for 1.20.2 is 764.
 * - `timeout`: (default: `900`) The number of seconds that the server must be empty before it is automatically shut
 * down by the gateway. Default: 15 minutes.
 * - `frequency`: (default: `30`) The number of seconds between regular pings to the server to check up on its current
 * status, note current players, etc. Default: every 30 seconds.
 */
object GatewayConfiguration {

    private const val DEFAULT_GATEWAY_PROPERTIES_PATH = "/gateway.properties"
    private const val DEFAULT_PORT = 25565
    private val DEFAULT_TIMEOUT = 15.toDuration(DurationUnit.MINUTES)
    private val DEFAULT_FREQUENCY = 30.toDuration(DurationUnit.SECONDS)

    var port: Int by lateinitval()
    var protocol: String by lateinitval()
    var timeout: Duration by lateinitval()
    var frequency: Duration by lateinitval()

    var propertyFile: Properties by lateinitval()

    private val log = getLogger {}

    fun init(args: Array<String>) {
        val propertiesPath = CommandLineArgs().add(
            StringParameter("configuration").alias("config").alias("c")
                .default(DEFAULT_GATEWAY_PROPERTIES_PATH))
            .parse(args).results["configuration"] as String

        log.info { "Properties path: $propertiesPath" }

        propertyFile = loadProperties(propertiesPath)

        port = propertyFile.retrieve("port", DEFAULT_PORT) { it.toInt() }
        protocol = propertyFile.retrieve("protocol")
        timeout = propertyFile.retrieve("timeout", DEFAULT_TIMEOUT) { it.toDuration(DurationUnit.SECONDS) }
        frequency = propertyFile.retrieve("frequency", DEFAULT_FREQUENCY) { it.toDuration(DurationUnit.SECONDS) }
    }
}