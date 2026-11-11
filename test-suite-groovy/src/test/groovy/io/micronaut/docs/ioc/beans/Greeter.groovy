package io.micronaut.docs.ioc.beans

// tag::class[]
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Inject

import java.time.Clock

@Introspected(includeInjected = true) // <1>
class Greeter {
    private Clock clock

    @Inject // <2>
    void setClock(Clock clock) {
        this.clock = clock
    }

    Clock getClock() {
        return clock
    }
}
// end::class[]
