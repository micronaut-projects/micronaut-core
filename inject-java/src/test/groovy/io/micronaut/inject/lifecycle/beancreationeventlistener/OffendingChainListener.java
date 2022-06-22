package io.micronaut.inject.lifecycle.beancreationeventlistener;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;

@Singleton
public class OffendingChainListener implements BeanCreatedEventListener<B> {

    OffendingChainListener(D d) {}

    @Override
    public B onCreated(BeanCreatedEvent<B> event) {
        return event.getBean();
    }
}
