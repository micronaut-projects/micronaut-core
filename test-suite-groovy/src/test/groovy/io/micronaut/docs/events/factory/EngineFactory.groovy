package io.micronaut.docs.events.factory

import io.micronaut.context.annotation.Factory

import javax.annotation.PostConstruct
import javax.inject.Singleton

// tag::class[]
@Factory
class EngineFactory {
    private V8Engine engine
    double rodLength = 5.7

    @PostConstruct
    void initialize() {
        engine = new V8Engine(rodLength: rodLength) // <2>
    }

    @Singleton
    Engine v8Engine() {
        return engine // <3>
    }
}
// tag::class[]
