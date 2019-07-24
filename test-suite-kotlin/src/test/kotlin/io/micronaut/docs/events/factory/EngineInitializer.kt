package io.micronaut.docs.events.factory

import io.micronaut.context.event.BeanInitializedEventListener
import io.micronaut.context.event.BeanInitializingEvent

import javax.inject.Singleton

// tag::class[]
@Singleton
class EngineInitializer : BeanInitializedEventListener<EngineFactory> { // <4>
    override fun onInitialized(event: BeanInitializingEvent<EngineFactory>): EngineFactory {
        val engineFactory = event.bean
        engineFactory.setRodLength(6.6)// <5>
        return event.bean as EngineFactory
    }
}
// tag::class[]
