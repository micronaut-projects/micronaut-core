package io.micronaut.inject.lifecycle.beancreationeventlistener;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
public class OffendingConstructorListener implements BeanCreatedEventListener<B> {

    static boolean initialized;
    static boolean executed;

    OffendingConstructorListener(A a, Provider<C> cProvider) {
        initialized = true;
    }

    @Override
    public B onCreated(BeanCreatedEvent<B> event) {
        executed = true;
        return event.getBean();
    }
}
