package io.micronaut.docs.context.events.application

// tag::imports[]
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.docs.context.events.SampleEvent
// end::imports[]
import javax.inject.Singleton

// tag::class[]
@Singleton
class SampleEventListener : ApplicationEventListener<SampleEvent> {
    var invocationCounter = 0

    override fun onApplicationEvent(event: SampleEvent) {
        invocationCounter++
    }
}
// end::class[]