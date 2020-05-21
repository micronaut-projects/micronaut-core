package io.micronaut.event;
import io.micronaut.context.event.ApplicationEvent;
import io.micronaut.context.event.ApplicationEventPublisher;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ExecutionException;

@Singleton
public class DeadlockProducer {
    private final ApplicationEventPublisher eventPublisher;

    @Inject
    public DeadlockProducer(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        try {
            eventPublisher.publishEventAsync(new ApplicationEvent("Event")).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    public String method() {
        return "value";
    }
}