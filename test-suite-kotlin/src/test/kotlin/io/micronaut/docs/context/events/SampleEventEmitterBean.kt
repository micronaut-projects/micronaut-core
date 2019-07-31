package io.micronaut.docs.context.events

// tag::class[]
import io.micronaut.context.event.ApplicationEventPublisher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SampleEventEmitterBean {

    @Inject
    internal var eventPublisher: ApplicationEventPublisher? = null

    fun publishSampleEvent() {
        eventPublisher!!.publishEvent(SampleEvent())
    }

}
// end::class[]