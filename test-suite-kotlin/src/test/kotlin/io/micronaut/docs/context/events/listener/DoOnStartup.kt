package io.micronaut.docs.context.events.listener

// tag::imports[]
import io.micronaut.discovery.event.ServiceStartedEvent
import io.micronaut.runtime.event.annotation.EventListener
// end::imports[]
import javax.inject.Singleton

// tag::class[]
@Singleton
class DoOnStartup {

    @EventListener
    internal fun onStartup(event: ServiceStartedEvent) {
        // do startup things
    }
}
// end::class[]