package io.micronaut.inject.lifecycle.beancreationeventlistener;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;

@Singleton
public class ACreatedListener implements BeanCreatedEventListener<A> {

    static boolean initialized;
    static boolean executed;

    ACreatedListener() {
        initialized = true;
    }

    @Override
    public A onCreated(BeanCreatedEvent<A> event) {
        executed = true;
        return event.getBean();
    }
}
