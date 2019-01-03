package io.micronaut.runtime.event.annotation;

import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.event.StartupEvent;

import javax.inject.Singleton;

@Singleton
public class OverloadedListener {

    StartupEvent startup;
    ShutdownEvent shutdown;

    @EventListener
    void receive(StartupEvent event) {
        this.startup = event;
    }

    @EventListener
    void receive(ShutdownEvent event) {
        this.shutdown = event;
    }
}
