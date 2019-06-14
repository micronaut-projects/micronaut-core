package io.micronaut.docs.context.events

// tag::class[]
import io.micronaut.context.event.ApplicationEventPublisher
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class MyBean {

    @Inject
    internal var eventPublisher: ApplicationEventPublisher? = null

    internal fun doSomething() {
        eventPublisher!!.publishEvent("SampleEvent")
    }

}
// end::class[]