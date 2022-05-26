package io.micronaut.runtime.event

import jakarta.inject.Singleton

@Singleton
class EventListenerImpl implements EventListenerContract {

    boolean called = false

    @Override
    void doOnEvent(MyEvent myEvent) {
        called = true
    }
}
