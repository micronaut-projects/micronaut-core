package io.micronaut.docs.context.events.application

// tag::imports[]
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.discovery.event.ServiceStartedEvent
// end::imports[]
import javax.inject.Singleton

// tag::class[]
@Singleton
class DoOnStartup : ApplicationEventListener<ServiceStartedEvent> {

    override fun onApplicationEvent(event: ServiceStartedEvent) {
        // respond to app event
    }
}
// end::class[]