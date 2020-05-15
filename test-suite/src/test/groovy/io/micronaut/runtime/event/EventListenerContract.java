package io.micronaut.runtime.event;

import io.micronaut.runtime.event.annotation.EventListener;

public interface EventListenerContract {

    @EventListener
    void doOnEvent(MyEvent myEvent);
}
