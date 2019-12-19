package io.micronaut.docs.events.factory;

import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanInitializingEvent;

import javax.inject.Singleton;

// tag::class[]
@Singleton
public class EngineInitializer implements BeanInitializedEventListener<EngineFactory> { // <4>
    @Override
    public EngineFactory onInitialized(BeanInitializingEvent<EngineFactory> event) {
        EngineFactory engineFactory = event.getBean();
        engineFactory.setRodLength(6.6);// <5>
        return engineFactory;
    }
}
// tag::class[]
