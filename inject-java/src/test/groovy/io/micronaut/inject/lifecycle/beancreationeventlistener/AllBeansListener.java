package io.micronaut.inject.lifecycle.beancreationeventlistener;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanInitializingEvent;
import jakarta.inject.Singleton;

@Singleton
public class AllBeansListener implements BeanCreatedEventListener<Object>, BeanInitializedEventListener<Object> {

    public AllBeansListener(
        BeanProvider<Environment> provider
// uncommenting the following two lines stack overflows, perhaps we should fail compilation?
//        , Environment environment,
//        H h
    ) {
    }

    static boolean executed = false;
    static boolean initialized = false;
    @Override
    public Object onCreated(BeanCreatedEvent<Object> event) {
        executed = true;
        return event.getBean();
    }

    @Override
    public Object onInitialized(BeanInitializingEvent<Object> event) {
        initialized = true;
        return event.getBean();
    }
}
