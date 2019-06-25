package io.micronaut.docs.context.events.listener

// tag::imports[]
import io.micronaut.docs.context.events.SampleEvent
import io.micronaut.runtime.event.annotation.EventListener
// end::imports[]
import javax.inject.Singleton

// tag::class[]
@Singleton
class SampleEventListener {
    int invocationCounter = 0

    @EventListener
    void onSampleEvent(SampleEvent event) {
        invocationCounter++
    }
}
// end::class[]
