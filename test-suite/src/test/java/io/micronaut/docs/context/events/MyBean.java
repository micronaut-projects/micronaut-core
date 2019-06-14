package io.micronaut.docs.context.events;

// tag::class[]
import io.micronaut.context.event.ApplicationEventPublisher;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MyBean {

    @Inject
    ApplicationEventPublisher eventPublisher;

    void doSomething() {
        eventPublisher.publishEvent("SampleEvent");
    }

}
// end::class[]