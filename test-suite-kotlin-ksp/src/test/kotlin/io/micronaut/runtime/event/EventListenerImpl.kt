package io.micronaut.runtime.event

import jakarta.inject.Singleton

@Singleton
class EventListenerImpl : EventListenerContract {

    var called = false

    override fun doOnEvent(myEvent: MyEvent) {
        called = true
    }
}
