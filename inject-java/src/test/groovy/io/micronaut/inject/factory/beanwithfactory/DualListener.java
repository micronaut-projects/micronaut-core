package io.micronaut.inject.factory.beanwithfactory;


import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanInitializingEvent;

import javax.inject.Singleton;

@Singleton
public class DualListener implements BeanCreatedEventListener<BFactory>, BeanInitializedEventListener<BFactory> {
    public boolean initialized;
    public boolean created;

    @Override
    public BFactory onCreated(BeanCreatedEvent<BFactory> event) {
        this.created = true;
        return event.getBean();
    }

    @Override
    public BFactory onInitialized(BeanInitializingEvent<BFactory> event) {
        this.initialized = true;
        return event.getBean();
    }
}
