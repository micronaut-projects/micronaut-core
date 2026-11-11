package io.micronaut.docs.ioc.beans

// tag::class[]
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Inject
import java.time.Clock

@Introspected(includeInjected = true) // <1>
class Greeter {
    private var clock: Clock? = null

    @Inject // <2>
    fun setClock(clock: Clock) {
        this.clock = clock
    }

    fun getClock() = clock
}
// end::class[]
