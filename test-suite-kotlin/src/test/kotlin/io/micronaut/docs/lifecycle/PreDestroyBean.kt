package io.micronaut.docs.lifecycle

import javax.annotation.PreDestroy
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicBoolean

@Singleton
class PreDestroyBean : AutoCloseable {

    var stopped = AtomicBoolean(false)
    @PreDestroy
    @Throws(Exception::class)
    override fun close() {
        stopped.compareAndSet(false, true)
    }
}
