package com.nolanbarry.gateway.model

import com.nolanbarry.gateway.CommandLineArgs
import com.nolanbarry.gateway.IntParameter
import com.nolanbarry.gateway.alias

val COMMAND_LINE_CONFIGURATION = CommandLineArgs()
    .add(
        IntParameter("port")
             .alias("p")
             .default(25565))

data class Configuration(val gatewayPort: Int) {
    companion object {
        fun fromCommandLine(commandLineArgs: Array<String>): Configuration {
            val cl = COMMAND_LINE_CONFIGURATION.parse(commandLineArgs)
            return Configuration(
                cl["port"]
            )
        }
    }
}