package com.nolanbarry.gateway.utils

import java.lang.IllegalArgumentException
import java.lang.RuntimeException

abstract class Option<T>(val name: String, var defaultValue: T? = null) {
    val names: MutableList<String> = mutableListOf(name)
}

fun <T : Option<*>> T.alias(alias: String) = this.apply { names.add(alias) }

abstract class Parameter<T>(name: String) : Option<T>(name) {
    fun default(value: T) = apply { defaultValue = value }
    abstract fun parse(arg: String): T
}

class IntParameter(name: String) : Parameter<Int>(name) {
    override fun parse(arg: String) = arg.toInt()
}

class StringParameter(name: String) : Parameter<String>(name) {
    override fun parse(arg: String) = arg
}

class Flag(name: String) : Option<Boolean>(name, false)

class CommandLineArgs {
    private val options: MutableMap<String, Option<*>> = mutableMapOf()

    fun add(opt: Option<*>) = apply {
        options.putAll(opt.names.associateWith { opt })
    }

    data class CommandLineParseResult(val results: Map<String, *>) {
        inline operator fun <reified T> get(key: String): T {
            val optionValue = results[key]
            if (optionValue !is T) {
                val expected = T::class.simpleName ?: T::class.java.simpleName
                val actual = optionValue?.let { it::class.simpleName } ?: "null"
                throw IllegalArgumentException("Expected command line argument $key to be of type $expected, got $actual")
            }
            return optionValue
        }
    }

    fun parse(argv: Array<String>): CommandLineParseResult {
        val results = options
            .filterValues { option -> option.defaultValue != null }
            .mapValues { (_, option) -> option.defaultValue!! }
            .toMutableMap()
        val workingArgs = ArrayDeque(argv.toList().subList(0, argv.size))
        val seen = mutableSetOf<Option<*>>()
        while (workingArgs.isNotEmpty()) {
            val next = workingArgs.removeFirst()
            val isFullyQualified = next.startsWith("--")
            val name = next.removePrefix(if (isFullyQualified) "--" else "-")
            val isMultiFlag = !isFullyQualified && name.length > 1
            val allAliases = if (isMultiFlag) name.toList() else listOf(name)
            val allOptions = allAliases.map { alias ->
                options[alias] ?: throw IllegalArgumentException("Got unexpected command line arg $next")
            }
            allOptions.forEach { option -> seen.add(option) || throw IllegalArgumentException("'${option.name}' or an alias was provided more than once") }
            val allAreFlags = allOptions.all { option -> option is Flag }
            if (!allAreFlags && isMultiFlag) throw IllegalArgumentException("Multi-flag argument $next contains an option which requires an argument")
            if (allAreFlags) {
                results.putAll(allOptions.associate { option -> option.name to true })
            } else {
                val option = allOptions.first()
                if (option !is Parameter<*>) throw RuntimeException("Impossible state")
                if (workingArgs.isEmpty()) throw IllegalArgumentException("Missing parameter associated with option $next")
                val parameter = workingArgs.removeFirst()
                val value = option.parse(parameter)
                value ?: throw IllegalArgumentException("Argument $parameter to $next did not evaluate to a value")
                results.putAll(mapOf(option.name to value))
            }
        }
        return CommandLineParseResult(results)
    }
}