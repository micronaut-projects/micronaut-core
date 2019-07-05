package io.micronaut.docs.context.events.async

// tag::imports[]
import io.micronaut.docs.context.events.SampleEvent
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.scheduling.annotation.Async
// end::imports[]
import javax.inject.Singleton

// tag::class[]
@Singleton
open class SampleEventListener {

    @Volatile
    var invocationCounter = 0

    @EventListener
    @Async
    open fun onSampleEvent(event: SampleEvent) {
        println("Incrementing invocation counter...")
        invocationCounter++
    }
}
// end::class[]