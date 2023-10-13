package com.nolanbarry.gateway

import io.ktor.network.selector.*
import kotlinx.coroutines.Dispatchers

val SOCKET_SELECTOR = SelectorManager(Dispatchers.IO)
