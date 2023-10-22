package com.nolanbarry.gateway.utils

import com.nolanbarry.gateway.model.MisconfigurationException
import java.util.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration

val log = getLogger {}

val PROPERTY_IS_REQUIRED_BUT_MISSING =
    { name: String -> "The property '$name' is required, please add it to your properties file." }
val USING_DEFAULT_VALUE =
    { name: String, default: Any -> "Using default value '$default' for property '$name'" }

/** Retrieve the property with the specified name, transforming it as specified. If the property is not defined, return
 * `default`. If no `default` is provided and the property isn't defined, raise a [MisconfigurationException]. */
fun <T> Properties.retrieve(name: String, default: T? = null, cast: (String) -> T): T {
    return getProperty(name)?.let { cast(it) }
        ?: default?.also { log.info { USING_DEFAULT_VALUE(name, default) } }
        ?: throw MisconfigurationException(PROPERTY_IS_REQUIRED_BUT_MISSING(name))
}

/** Retrieve the property with the specified name, or `default`. Raise a [MisconfigurationException] if the property
 * isn't defined and there is no `default`. */
fun Properties.retrieve(name: String, default: String? = null): String = retrieve(name, default) { it }

/** Shortcut for String.toInt().toDuration() */
fun String.toDuration(unit: DurationUnit) = toInt().toDuration(unit)