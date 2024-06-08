package com.nolanbarry.gateway.config

import com.nolanbarry.gateway.model.MisconfigurationException
import com.nolanbarry.gateway.utils.PROPERTY_IS_REQUIRED_BUT_MISSING
import com.nolanbarry.gateway.utils.USING_DEFAULT_VALUE
import com.nolanbarry.gateway.utils.log
import java.util.Properties
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation

interface ConfigurationSource {
    val properties: Properties
}

@Target(AnnotationTarget.PROPERTY)
annotation class Key(val key: String)

class ConfigurableProperty<T : Any>(
    private val defaultValue: T? = null,
    private val conversion: (String) -> T
) {
    private var initialized = false
    private var value: T? = null

    operator fun getValue(gateway: ConfigurationSource, property: KProperty<*>): T = synchronized(this) {
        if (!initialized) {
            val key = property.findAnnotation<Key>()?.key ?: property.name
            val providedValue = gateway.properties.getProperty(key)
            val actualValue = providedValue?.let { conversion(it) }
                ?: defaultValue?.also { log.info { USING_DEFAULT_VALUE(key, it) } }
                ?: throw MisconfigurationException(PROPERTY_IS_REQUIRED_BUT_MISSING(key))
            value = actualValue
            initialized = true
        }
        value!!
    }
}

fun <T : Any> property(defaultValue: T? = null, conversion: (String) -> T) =
    ConfigurableProperty(defaultValue, conversion)

fun property(defaultValue: String? = null) = ConfigurableProperty(defaultValue) { it }