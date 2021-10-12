package io.micronaut.runtime.event

import io.micronaut.runtime.event.annotation.EventListener

interface EventListenerContract {

    @EventListener
    void doOnEvent(MyEvent myEvent)
}
