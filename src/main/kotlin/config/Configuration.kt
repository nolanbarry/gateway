package com.nolanbarry.gateway.config

object Configuration {

    fun init(args: Array<String>) {
        gateway = GatewayConfiguration.fromCommandLine(args)
    }

    lateinit var gateway: GatewayConfiguration
    val delegate = DelegateConfiguration.load()
}