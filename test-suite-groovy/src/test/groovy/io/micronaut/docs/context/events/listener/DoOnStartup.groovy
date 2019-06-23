package io.micronaut.docs.context.events.listener;

// tag::imports[]
import io.micronaut.discovery.event.ServiceStartedEvent
import io.micronaut.runtime.event.annotation.EventListener
// end::imports[]
import javax.inject.Singleton


// tag::class[]
@Singleton
class DoOnStartup  {

    @EventListener
    void onStartup(ServiceStartedEvent event) {
        // do startup things
    }
}
// end::class[]