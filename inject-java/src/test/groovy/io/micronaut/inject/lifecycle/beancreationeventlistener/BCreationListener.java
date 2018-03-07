package io.micronaut.inject.lifecycle.beancreationeventlistener;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;

import javax.inject.Singleton;

@Singleton
public class BCreationListener implements BeanCreatedEventListener<B> {

    @Override
    public B onCreated(BeanCreatedEvent<B> event) {
        ChildB childB = new ChildB(event.getBean());
        childB.name = "good";
        return childB;
    }
}
