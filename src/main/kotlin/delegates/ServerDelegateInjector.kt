package com.nolanbarry.gateway.delegates

import com.nolanbarry.gateway.config.Configuration
import com.nolanbarry.gateway.utils.getLogger
import kotlin.reflect.full.primaryConstructor
import kotlin.system.exitProcess

object ServerDelegateInjector {

    private val log = getLogger {}
    fun get(): ServerDelegate {
        val delegatesPackage = this::class.java.packageName
        val delegateClasspath = "$delegatesPackage.${Configuration.delegate.classpath}"

        log.debug { "Building server delegate." }
        log.debug { "Package: $delegatesPackage" }
        log.debug { "Relative path: ${Configuration.delegate.classpath}" }
        log.debug { "Classpath: $delegateClasspath" }

        val delegateClass = Class.forName(delegateClasspath).kotlin
        try {
            return delegateClass.primaryConstructor!!.call() as ServerDelegate
        } catch (e: Exception) {
            log.error(e) { "Failed to create server delegate" }
            exitProcess(1)
        }
    }
}