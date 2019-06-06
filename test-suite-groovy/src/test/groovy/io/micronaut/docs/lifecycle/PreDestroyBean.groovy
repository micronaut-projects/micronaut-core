package io.micronaut.docs.lifecycle

// tag::class[]
import javax.annotation.PreDestroy // <1>
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Singleton

@Singleton
class PreDestroyBean implements AutoCloseable {

    AtomicBoolean stopped = new AtomicBoolean(false)

    @PreDestroy // <2>
    @Override
    void close() throws Exception {
        stopped.compareAndSet(false, true)
    }
}
// end::class[]