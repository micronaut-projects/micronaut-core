package io.micronaut.inject.lifecycle.beancreationeventlistener;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
public class OffendingFieldListener implements BeanCreatedEventListener<B> {

    @Inject A a;
    @Inject Provider<C> cProvider;

    @Override
    public B onCreated(BeanCreatedEvent<B> event) {
        return event.getBean();
    }
}
