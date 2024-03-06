package com.nolanbarry.gateway.utils

import com.nolanbarry.gateway.delegates.ServerDelegate
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.jvmName

/* PROPERTY INITIALIZATION */
val PROPERTY_IS_REQUIRED_BUT_MISSING =
    { name: String -> "The property '$name' is required, please add it to your properties file." }
val USING_DEFAULT_VALUE =
    { name: String, default: Any -> "Using default value '$default' for property '$name'" }
val INVALID_PROPERTIES_PATH = { path: String -> "Could not find properties file on classpath at '$path'" }

/* REFLECTION */
val CLASS_HAS_NO_PRIMARY_CONSTRUCTOR =
    { which: KClass<*> -> "Class ${which.jvmName} has no primary constructor and can't be built." }
val UNSUPPORTED_CONSTRUCTION_TYPE = { which: KClass<*>, who: String, what: KClass<*> ->
    "Parameter '$who' in constructor of ${which.jvmName} is of an unsupported type '${what.jvmName}'"
}
val CLASS_MUST_BE_SUBTYPE_OF =
    { sup: KClass<*>, sub: KClass<*> -> "Class ${sub.jvmName} must be a subtype of ${sup.jvmName}." }
val ILLEGAL_OPTIONAL_AND_NULLABLE_CONSTRUCTOR_ARG = { name: String, constructor: KClass<*> ->
    "Parameter '$name' in constructor of ${constructor.jvmName} is both optional and nullable, which gateway does not" +
            "support loading from a properties file."
}

/* SERVER DELEGATE */
val FAILED_TO_DO_AFTER_X_ATTEMPTS = { what: String, attempts: Int -> "Failed to $what server after $attempts attempts" }
