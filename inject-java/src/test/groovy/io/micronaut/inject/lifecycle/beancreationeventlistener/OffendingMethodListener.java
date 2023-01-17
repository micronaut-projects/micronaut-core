package io.micronaut.inject.lifecycle.beancreationeventlistener;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
public class OffendingMethodListener implements BeanCreatedEventListener<B> {

    static boolean initialized;
    static boolean executed;

    OffendingMethodListener() {
        initialized = true;
    }

    @Inject
    void setA(A a) {}
    @Inject
    void setC(Provider<C> cProvider) {}

    @Override
    public B onCreated(BeanCreatedEvent<B> event) {
        executed = true;
        return event.getBean();
    }
}
