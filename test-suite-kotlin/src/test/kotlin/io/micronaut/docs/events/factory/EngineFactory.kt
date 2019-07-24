package io.micronaut.docs.events.factory

import io.micronaut.context.annotation.Factory

import javax.annotation.PostConstruct
import javax.inject.Singleton

// tag::class[]
@Factory
class EngineFactory {

    private var engine: V8Engine? = null
    private var rodLength = 5.7

    @PostConstruct
    fun initialize() {
        engine = V8Engine(rodLength) // <2>
    }

    @Singleton
    fun v8Engine(): Engine? {
        return engine// <3>
    }

    fun setRodLength(rodLength: Double) {
        this.rodLength = rodLength
    }
}
// end::class[]