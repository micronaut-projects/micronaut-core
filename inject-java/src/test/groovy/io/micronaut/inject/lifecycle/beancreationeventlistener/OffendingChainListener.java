package io.micronaut.inject.lifecycle.beancreationeventlistener;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;

@Singleton
public class OffendingChainListener implements BeanCreatedEventListener<B> {

    static boolean initialized;
    static boolean executed;

    OffendingChainListener(D d) {
        initialized = true;
    }

    @Override
    public B onCreated(BeanCreatedEvent<B> event) {
        executed = true;
        return event.getBean();
    }
}
