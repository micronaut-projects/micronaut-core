package io.micronaut.docs.lifecycle

// tag::imports[]

import javax.annotation.PostConstruct
import javax.inject.Singleton

// end::imports[]

// tag::class[]
@Singleton
class V8Engine : Engine {
    override val cylinders = 8
    var isIntialized = false
        private set // <2>

    override fun start(): String {
        check(isIntialized) { "Engine not initialized!" }

        return "Starting V8"
    }

    @PostConstruct // <3>
    fun initialize() {
        this.isIntialized = true
    }
}
// end::class[]