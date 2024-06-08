package com.nolanbarry.gateway.config

import com.nolanbarry.gateway.model.MisconfigurationException
import com.nolanbarry.gateway.utils.CommandLineArgs
import com.nolanbarry.gateway.utils.StringParameter
import com.nolanbarry.gateway.utils.alias
import com.nolanbarry.gateway.utils.getLogger
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** This object loads and encapsulates the `gateway.properties` file, which is the basic configuration file which the
 * user of this program uses to... configure the gateway. These are the options which are available:
 * - `delegate`: Which delegate to use: `aws` or `local`. One must be chosen, and different delegates require
 *   additional unique arguments.
 * - `port`: (default: `25565`) The port to host the gateway on. Defaults: `25565`, (Minecraft's default server port)
 * - `protocol`: The protocol to use - this is the protocol number used internally by the Minecraft server protocol.
 * Grab the protocol from https://wiki.vg/Protocol_version_numbers - i.e., The protocol number for 1.20.2 is 764.
 * - `timeout`: (default: `900`) The number of seconds that the server must be empty before it is automatically shut
 * down by the gateway. Default: 15 minutes.
 * - `frequency`: (default: `30`) The number of seconds between regular pings to the server to check up on its current
 * status, note current players, etc. Default: every 30 seconds.
 */
open class BaseConfiguration(args: Array<String>, from: KClass<*>): ConfigurationSource {

    companion object {
        private const val DEFAULT_PROPERTIES_FILENAME = "gateway.properties"
        private const val DEFAULT_GATEWAY_PORT = 25565
        private val DEFAULT_TIMEOUT = 15.minutes
        private val DEFAULT_FREQUENCY = 30.seconds
    }

    private val log = getLogger {}
    private val jarDirectory = File(from.java.protectionDomain.codeSource.location.toURI())
        .run { if (isDirectory) path else parent }
        .let { Path(it) }
    private val userDirectory = System.getenv("user.dir")?.let { Path(it) }

    override val properties = run {
        val propertiesPathOverride = CommandLineArgs().add(
            StringParameter("configuration").alias("config").alias("c")
        ).parse(args).results["configuration"]?.let { Path(it as String) }

        val potentialPropertiesPaths = getPotentialPropertiesPaths(propertiesPathOverride)
        loadProperties(potentialPropertiesPaths)
    }

    val delegate: String by property()
    val port: Int by property(DEFAULT_GATEWAY_PORT) { it.toInt() }
    val protocol: String by property()
    val timeout by property(DEFAULT_TIMEOUT) { it.toInt().seconds }
    val frequency by property(DEFAULT_FREQUENCY) { it.toInt().seconds }

    data class PotentialResourcePath(val path: String, val onClasspath: Boolean)

    /** Return a list of [PotentialResourcePath]s, ranked by precedence. Each path is checked in order to see if a
     * configuration file exists there. */
    private fun getPotentialPropertiesPaths(directoryOverride: Path?) = buildList {
        infix operator fun Path.plus(path: Path) = Path(pathString, path.pathString).pathString
        infix operator fun Path.plus(path: String) = Path(pathString, path).pathString

        if (directoryOverride != null) {
            val relativePathOverride = directoryOverride.run {
                if (isDirectory()) Path(pathString, DEFAULT_PROPERTIES_FILENAME)
                else this
            }

            if (relativePathOverride.isAbsolute) {
                add(PotentialResourcePath(relativePathOverride.pathString, false))
            } else {
                add(PotentialResourcePath(jarDirectory + relativePathOverride, false))
                userDirectory?.let { add(PotentialResourcePath(it + relativePathOverride, false)) }
            }
        } else {
            add(PotentialResourcePath(jarDirectory + DEFAULT_PROPERTIES_FILENAME, false))
            userDirectory?.let { add(PotentialResourcePath(it + DEFAULT_PROPERTIES_FILENAME, false)) }
            add(PotentialResourcePath("/$DEFAULT_PROPERTIES_FILENAME", true))
        }
    }

    private fun loadProperties(searchLocations: List<PotentialResourcePath>): Properties {

        fun fromClasspath(path: String): InputStream? = this::class.java.getResourceAsStream(path)
        fun fromFilepath(path: String): InputStream? = runCatching { File(path).inputStream() }.getOrNull()

        val properties = Properties()
        for (path in searchLocations) {
            if (path.onClasspath) log.debug { "Searching for properties file on classpath at '${path.path}'" }
            else log.debug { "Searching for properties file in '${path.path}" }

            val stream = (if (path.onClasspath) fromClasspath(path.path) else fromFilepath(path.path)) ?: continue
            log.debug { "'${path.path}' was a hit" }
            properties.load(stream)
            return properties
        }

        throw MisconfigurationException(
            "Couldn't load properties file! Tried ${searchLocations.size} locations:\n${
                searchLocations.joinToString { "  - ${it.path} (${if (it.onClasspath) "classpath" else "file"})\n" }
            }"
        )
    }
}