package io.micronaut.docs.context.events.async

// tag::imports[]
import io.micronaut.docs.context.events.SampleEvent
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.scheduling.annotation.Async
import java.util.concurrent.atomic.AtomicInteger
// end::imports[]
import javax.inject.Singleton

// tag::class[]
@Singleton
open class SampleEventListener {

    var invocationCounter = AtomicInteger(0)

    @EventListener
    @Async
    open fun onSampleEvent(event: SampleEvent) {
        println("Incrementing invocation counter...")
        invocationCounter.getAndIncrement()
    }
}
// end::class[]