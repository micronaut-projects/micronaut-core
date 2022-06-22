package io.micronaut.inject.lifecycle.beancreationeventlistener;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;

@Singleton
public class OffendingInterfaceListener implements BeanCreatedEventListener<B> {

    OffendingInterfaceListener(AInterface a) {}

    @Override
    public B onCreated(BeanCreatedEvent<B> event) {
        return event.getBean();
    }
}
