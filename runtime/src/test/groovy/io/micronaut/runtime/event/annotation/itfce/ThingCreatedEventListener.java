package io.micronaut.runtime.event.annotation.itfce;

import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.annotation.Async;

public interface ThingCreatedEventListener {
    @Async
    @EventListener
    void onThingCreated(ThingCreatedEvent event);
}
