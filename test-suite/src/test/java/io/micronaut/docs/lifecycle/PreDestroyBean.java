package io.micronaut.docs.lifecycle;

// tag::class[]
import javax.annotation.PreDestroy; // <1>
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class PreDestroyBean implements AutoCloseable {

    AtomicBoolean stopped = new AtomicBoolean(false);

    @PreDestroy // <2>
    @Override
    public void close() throws Exception {
        stopped.compareAndSet(false, true);
    }
}
// end::class[]