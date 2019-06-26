package io.micronaut.docs.lifecycle

// tag::class[]
import javax.annotation.PreDestroy // <1>
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicBoolean

@Singleton
class PreDestroyBean : AutoCloseable {

    var stopped = AtomicBoolean(false)

    @PreDestroy // <2>
    @Throws(Exception::class)
    override fun close() {
        stopped.compareAndSet(false, true)
    }
}
// end::class[]