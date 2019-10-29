package io.micronaut.docs.lifecycle

// tag::class[]

import javax.annotation.PreDestroy
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicBoolean

@Singleton
class PreDestroyBean : AutoCloseable {

    internal var stopped = AtomicBoolean(false)

    @PreDestroy // <2>
    @Throws(Exception::class)
    override fun close() {
        stopped.compareAndSet(false, true)
    }
}
// end::class[]