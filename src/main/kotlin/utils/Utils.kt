package com.nolanbarry.gateway.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.reflect.KProperty
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

@Suppress("UNCHECKED_CAST")
class LateInitVal<T> {
    @Volatile
    var isInitialized = false
    @Volatile
    var backing: T? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = synchronized(this) {
        if (!isInitialized) throw IllegalStateException("${property.name} was accessed before being initialized")
        return backing as T
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = synchronized(this) {
        if (isInitialized) throw IllegalStateException("Attempting to initialize ${property.name} a second time")
        isInitialized = true
        backing = value
    }
}

fun <T> lateinitval() = LateInitVal<T>()