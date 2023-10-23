package com.nolanbarry.gateway.config

import com.nolanbarry.gateway.utils.CommandLineArgs
import com.nolanbarry.gateway.utils.ResourceLoader.loadProperties
import com.nolanbarry.gateway.utils.StringParameter
import com.nolanbarry.gateway.utils.alias
import com.nolanbarry.gateway.utils.getLogger
import com.nolanbarry.gateway.utils.retrieve
import com.nolanbarry.gateway.utils.toDuration
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration


class GatewayConfiguration private constructor(
    val port: Int,
    val protocol: String,
    val timeout: Duration,
    val frequency: Duration,
) {
    companion object {
        private val log = getLogger {}
        private const val DEFAULT_GATEWAY_PROPERTIES_PATH = "/gateway.properties"
        private const val DEFAULT_PORT = 25565
        private val DEFAULT_TIMEOUT = 15.toDuration(DurationUnit.MINUTES)
        private val DEFAULT_FREQUENCY = 30.toDuration(DurationUnit.SECONDS)

        private fun fromPropertyFile(filename: String): GatewayConfiguration {
            val properties = loadProperties(filename)

            return GatewayConfiguration(
                port = properties.retrieve("port", DEFAULT_PORT) { it.toInt() },
                protocol = properties.retrieve("protocol"),
                timeout = properties.retrieve("timeout", DEFAULT_TIMEOUT) { it.toDuration(DurationUnit.SECONDS) },
                frequency = properties.retrieve("frequency", DEFAULT_FREQUENCY) { it.toDuration(DurationUnit.SECONDS) }
            )
        }

        fun fromCommandLine(args: Array<String>): GatewayConfiguration {
            val propertiesPath = CommandLineArgs().add(
                StringParameter("configuration").alias("config").alias("c").default(DEFAULT_GATEWAY_PROPERTIES_PATH))
                .parse(args).results["configuration"] as String

            log.info { "Properties path: $propertiesPath" }

            return fromPropertyFile(propertiesPath)
        }
    }
}