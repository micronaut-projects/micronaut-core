package io.micronaut.inject.lifecycle.beancreationeventlistener;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;

@Singleton
public class CCreatedListener implements BeanCreatedEventListener<C> {

    static boolean initialized;
    static boolean executed;

    CCreatedListener() {
        initialized = true;
    }

    @Override
    public C onCreated(BeanCreatedEvent<C> event) {
        executed = true;
        return event.getBean();
    }
}
