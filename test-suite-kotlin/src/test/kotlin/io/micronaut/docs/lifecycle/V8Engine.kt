package io.micronaut.docs.lifecycle

import javax.annotation.PostConstruct
import javax.inject.Singleton

@Singleton
class V8Engine : Engine {

    override var cylinders = 8
    var initialized = false
    override fun start(): String {
        if (!initialized) throw IllegalStateException("Engine not initialized!")

        return "Starting V8"
    }

    @PostConstruct
    fun initialize() {
        this.initialized = true
    }

    fun isInitialized(): Boolean {
        return initialized
    }
}
