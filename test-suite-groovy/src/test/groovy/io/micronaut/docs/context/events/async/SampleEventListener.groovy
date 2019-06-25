package io.micronaut.docs.context.events.async

// tag::imports[]
import io.micronaut.docs.context.events.SampleEvent
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.scheduling.annotation.Async
// end::imports[]
import javax.inject.Singleton

@Singleton
class SampleEventListener {
    int invocationCounter = 0

    @EventListener
    @Async
    void onSampleEvent(SampleEvent event) {
        invocationCounter++
    }
}
