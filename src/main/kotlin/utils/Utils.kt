package com.nolanbarry.gateway.utils

import io.github.oshai.kotlinlogging.KotlinLogging

fun getLogger(context: () -> Unit) = KotlinLogging.logger(
    context.javaClass.name
        .substringBefore("Kt$").substringBefore("$")
        .removePrefix("com.nolanbarry.gateway.")
        .lowercase())

fun getLogger(name: String) = KotlinLogging.logger(name)