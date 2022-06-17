package io.micronaut.inject.lifecycle.beancreationeventlistener;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;

@Singleton
public class ACreatedListener implements BeanCreatedEventListener<A> {
    @Override
    public A onCreated(BeanCreatedEvent<A> event) {
        return event.getBean();
    }
}
