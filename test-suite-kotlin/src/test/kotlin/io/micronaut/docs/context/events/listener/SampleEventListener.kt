package io.micronaut.docs.context.events.listener

// tag::imports[]
import io.micronaut.docs.context.events.SampleEvent
import io.micronaut.runtime.event.annotation.EventListener
// end::imports[]
import javax.inject.Singleton

// tag::class[]
@Singleton
class SampleEventListener {
    var invocationCounter = 0

    @EventListener
    internal fun onSampleEvent(event: SampleEvent) {
        invocationCounter++
    }
}
// end::class[]