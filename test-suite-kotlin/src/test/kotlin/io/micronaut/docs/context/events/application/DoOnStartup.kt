package io.micronaut.docs.context.events.application

// tag::imports[]
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.discovery.event.ServiceStartedEvent
import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class DoOnStartup : ApplicationEventListener<ServiceStartedEvent> {

// end::class[]
    var invocationCounter = 0
// tag::class[]

    override fun onApplicationEvent(event: ServiceStartedEvent) {
        // respond to app event

// end::class[]
    invocationCounter++
// tag::class[]
    }
}
// end::class[]