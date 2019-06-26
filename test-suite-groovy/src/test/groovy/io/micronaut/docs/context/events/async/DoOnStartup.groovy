package io.micronaut.docs.context.events.async

// tag::imports[]
import io.micronaut.discovery.event.ServiceStartedEvent
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.scheduling.annotation.Async
// end::imports[]
import javax.inject.Singleton



// tag::class[]
@Singleton
class DoOnStartup  {

    @EventListener
    @Async
    void onStartup(ServiceStartedEvent event) {
        // handle long events async
    }
}
// end::class[]