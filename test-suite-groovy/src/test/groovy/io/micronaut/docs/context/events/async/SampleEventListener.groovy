package io.micronaut.docs.context.events.async

// tag::imports[]
import io.micronaut.docs.context.events.SampleEvent
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.scheduling.annotation.Async
// end::imports[]
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicInteger

// tag::class[]
@Singleton
class SampleEventListener {
    AtomicInteger invocationCounter = new AtomicInteger(0)

    @EventListener
    @Async
    void onSampleEvent(SampleEvent event) {
        invocationCounter.getAndIncrement()
    }
}
// end::class[]
