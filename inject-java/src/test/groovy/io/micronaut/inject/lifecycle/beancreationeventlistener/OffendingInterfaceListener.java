package io.micronaut.inject.lifecycle.beancreationeventlistener;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;

@Singleton
public class OffendingInterfaceListener implements BeanCreatedEventListener<B> {

    static boolean initialized;
    static boolean executed;

    OffendingInterfaceListener(AInterface a) {
        initialized = true;
    }

    @Override
    public B onCreated(BeanCreatedEvent<B> event) {
        executed = true;
        return event.getBean();
    }
}
