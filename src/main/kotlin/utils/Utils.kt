package com.nolanbarry.gateway.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

fun getLogger(context: () -> Unit) = KotlinLogging.logger(
    context.javaClass.name
        .substringBefore("Kt$").substringBefore("$")
        .removePrefix("com.nolanbarry.gateway.")
        .lowercase())

fun getLogger(name: String) = KotlinLogging.logger(name)

/** Create a [Channel] that produces a [Unit] every [interval] units of time.
 * Caller can safely close the channel at any time. */
suspend fun createMetronome(interval: Duration) = coroutineScope {
    val metronome = Channel<Unit>(Channel.CONFLATED)
    launch {
        while (true) {
            delay(interval)
            metronome.trySend(Unit).isClosed || return@launch
        }
    }
    metronome
}