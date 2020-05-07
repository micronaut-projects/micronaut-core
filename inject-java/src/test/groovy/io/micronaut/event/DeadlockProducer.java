package io.micronaut.event;
import io.micronaut.context.event.ApplicationEvent;
import io.micronaut.context.event.ApplicationEventPublisher;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DeadlockProducer {
    private final ApplicationEventPublisher eventPublisher;

    @Inject
    public DeadlockProducer(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        Thread thread = new Thread(() -> {
            eventPublisher.publishEvent(new ApplicationEvent("Event"));
        });

        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
        }
    }

    public String method() {
        return "value";
    }
}