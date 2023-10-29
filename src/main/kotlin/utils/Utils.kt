package com.nolanbarry.gateway.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

fun getLogger(context: () -> Unit) = KotlinLogging.logger(
    context.javaClass.name
        .substringBefore("Kt$").substringBefore("$")
        .removePrefix("com.nolanbarry.gateway.")
        .lowercase())

fun getLogger(name: String) = KotlinLogging.logger(name)

/** Create a [Flow] that emits a [Unit] every [interval] units of time. */
suspend fun createMetronome(interval: Duration) = flow {
    while (true) {
        delay(interval)
        emit(Unit)
    }
}