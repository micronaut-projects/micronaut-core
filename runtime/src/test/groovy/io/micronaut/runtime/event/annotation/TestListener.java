package io.micronaut.runtime.event.annotation;

import io.micronaut.context.event.StartupEvent;

import javax.inject.Singleton;

@Singleton
public class TestListener {

    boolean invoked = false;

    @EventListener
    void onStartup(StartupEvent event) {
        invoked = true;
    }

    public boolean isInvoked() {
        return invoked;
    }
}
