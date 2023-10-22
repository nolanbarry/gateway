package com.nolanbarry.gateway.utils

import com.nolanbarry.gateway.model.MisconfigurationException
import java.io.InputStream
import java.util.Properties

object ResourceLoader {
    private val INVALID_PROPERTIES_PATH = { path: String -> "Could not find properties file on classpath at '$path'" }

    private fun stream(path: String): InputStream? = {}::class.java.getResourceAsStream(path)
    fun loadProperties(path: String): Properties = Properties().apply {
        load(stream(path) ?: throw MisconfigurationException(INVALID_PROPERTIES_PATH(path)))
    }
}