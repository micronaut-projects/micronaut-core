package io.micronaut.messaging.annotation;

import io.micronaut.runtime.event.ApplicationShutdownEvent;
import io.micronaut.runtime.event.ApplicationStartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;

@Singleton
public class EventCatcher {

    boolean applicationStarted;
    boolean applicationStopped;

    @EventListener
    void on(ApplicationStartupEvent startupEvent) {
        applicationStarted = true;
    }

    @EventListener
    void on(ApplicationShutdownEvent shutdownEvent) {
        applicationStopped = true;
    }

}
