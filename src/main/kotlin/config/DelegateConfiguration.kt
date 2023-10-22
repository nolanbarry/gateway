package com.nolanbarry.gateway.config

import com.nolanbarry.gateway.utils.ResourceLoader.loadProperties

data class DelegateConfiguration(
    val classpath: String
) {
    companion object {
        private const val DELEGATE_PROPERTIES_PATH = "/local/delegate.properties"

        fun load(): DelegateConfiguration {
            val properties = loadProperties(DELEGATE_PROPERTIES_PATH)

            return DelegateConfiguration(
                classpath = properties.getProperty("classpath")
            )
        }
    }
}