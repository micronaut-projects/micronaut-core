package io.micronaut.inject.lifecycle.beancreationeventlistener;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
public class OffendingFieldListener implements BeanCreatedEventListener<B> {

    static boolean initialized;
    static boolean executed;

    @Inject A a;
    @Inject Provider<C> cProvider;

    OffendingFieldListener() {
        initialized = true;
    }

    @Override
    public B onCreated(BeanCreatedEvent<B> event) {
        executed = true;
        return event.getBean();
    }
}
