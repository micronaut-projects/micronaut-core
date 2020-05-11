package io.micronaut.runtime.event

import javax.inject.Singleton

@Singleton
class EventListenerImpl implements EventListenerContract {

    boolean called = false

    @Override
    void doOnEvent(MyEvent myEvent) {
        called = true
    }
}
