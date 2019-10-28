package io.micronaut.docs.lifecycle

// tag::class[]
import java.util.concurrent.atomic.AtomicBoolean

class Connection {

    internal var stopped = AtomicBoolean(false)

    fun stop() { // <2>
        stopped.compareAndSet(false, true)
    }

}
// end::class[]
