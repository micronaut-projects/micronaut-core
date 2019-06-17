package io.micronaut.docs.context.events.listener

// tag::imports[]
import io.micronaut.discovery.event.ServiceStartedEvent
import io.micronaut.runtime.event.annotation.EventListener
import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class DoOnStartup {

// end::class[]
    var invocationCounter = 0
// tag::class[]

    @EventListener
    internal fun onStartup(event: ServiceStartedEvent) {
        // do startup things
// end::class[]
        invocationCounter++
// tag::class[]
    }
}
// end::class[]