package com.nolanbarry.gateway.utils

import com.nolanbarry.gateway.model.MisconfigurationException
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.jvmErasure
import kotlin.time.DurationUnit
import kotlin.time.toDuration

val log = getLogger {}

/** Retrieve the property with the specified name, transforming it as specified. If the property is not defined, return
 * [default]. If no [default] is provided and the property isn't defined, raise a [MisconfigurationException]. */
fun <T> Properties.retrieve(name: String, default: T? = null, cast: (String) -> T): T {
    return getProperty(name)?.let { cast(it) }
        ?: default?.also { log.info { USING_DEFAULT_VALUE(name, default) } }
        ?: throw MisconfigurationException(PROPERTY_IS_REQUIRED_BUT_MISSING(name))
}

/** Retrieve the property with the specified name, or [default]. Raise a [MisconfigurationException] if the property
 * isn't defined and there is no [default]. */
fun Properties.retrieve(name: String, default: String? = null): String = retrieve(name, default) { it }

/** Create a given data class object from this properties object. Throws [MisconfigurationException] if required data is
 * missing from the properties file and an [IllegalArgumentException] if the given class [dataClass] can't be
 * initialized. */
fun <T: Any> Properties.loadInto(dataClass: KClass<T>): T {
    val constructor = dataClass.primaryConstructor
        ?: throw IllegalArgumentException(CLASS_HAS_NO_PRIMARY_CONSTRUCTOR(dataClass))


    val signature = constructor.valueParameters.associateNotNull { param ->
        val name = param.name ?: "Unnamed parameter"
        val property = getProperty(name)

        val propertyMustBeDefined = !param.isOptional && !param.type.isMarkedNullable
        if (property == null && propertyMustBeDefined)
            throw MisconfigurationException(PROPERTY_IS_REQUIRED_BUT_MISSING(name))
        else if (property == null) return@associateNotNull null

        param to when (param.type) {
            String::class -> property
            Int::class -> property.toInt()
            Double::class -> property.toDouble()
            else -> throw IllegalArgumentException(UNSUPPORTED_CONSTRUCTION_TYPE(dataClass, name, param.type.jvmErasure))
        }
    }

    return constructor.callBy(signature)
}

fun String.toDuration(unit: DurationUnit) = toInt().toDuration(unit)
fun String.capitalize() = replaceFirstChar { it.titlecase(Locale.ENGLISH) }

fun <T, K, V> Iterable<T>.associateNotNull(operation: (T) -> Pair<K, V>?): Map<K, V> =
    this.mapNotNull(operation).toMap()