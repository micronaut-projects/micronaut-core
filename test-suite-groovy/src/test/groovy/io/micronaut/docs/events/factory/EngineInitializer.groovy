package io.micronaut.docs.events.factory

// tag::imports[]
import io.micronaut.context.event.BeanInitializedEventListener
import io.micronaut.context.event.BeanInitializingEvent

import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class EngineInitializer implements BeanInitializedEventListener<EngineFactory> { // <4>
    @Override
    EngineFactory onInitialized(BeanInitializingEvent<EngineFactory> event) {
        EngineFactory engineFactory = event.bean
        engineFactory.rodLength = 6.6 // <5>
        return event.bean
    }
}
// end::class[]
