package io.micronaut.docs.context.events.application

// tag::imports[]
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.docs.context.events.SampleEvent
// end::imports[]
import javax.inject.Singleton

// tag::class[]
@Singleton
class SampleEventListener implements ApplicationEventListener<SampleEvent> {
    int invocationCounter = 0

    @Override
    void onApplicationEvent(SampleEvent event) {
        invocationCounter++
    }
}
// end::class[]
