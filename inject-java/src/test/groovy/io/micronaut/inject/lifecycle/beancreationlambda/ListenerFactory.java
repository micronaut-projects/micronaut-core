package io.micronaut.inject.lifecycle.beancreationlambda;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.event.StartupEvent;

import javax.inject.Singleton;

@Factory
public class ListenerFactory {

    @Singleton
    BeanCreatedEventListener<B> onCreateB() {
        return event -> {
            ChildB childB = new ChildB(event.getBean());
            childB.name = "good";
            return childB;
        };
    }

    @Singleton
    ApplicationEventListener<StartupEvent> onStartup() {
        return event -> System.out.println("Starting up!");
    }
}
