package io.micronaut.docs.context.events

// tag::class[]
import io.micronaut.context.event.ApplicationEventPublisher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SampleEventEmitterBean {

    @Inject
    ApplicationEventPublisher eventPublisher

    void publishSampleEvent() {
        eventPublisher.publishEvent(new SampleEvent())
    }

}
// end::class[]