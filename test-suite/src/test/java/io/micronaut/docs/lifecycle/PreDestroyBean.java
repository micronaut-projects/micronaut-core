package io.micronaut.docs.lifecycle;

import javax.annotation.PreDestroy; // <1>
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;

// tag::class[]
@Singleton
public class PreDestroyBean implements AutoCloseable {

    private AtomicBoolean stopped = new AtomicBoolean(false);

    @PreDestroy // <2>
    @Override
    public void close() throws Exception {
        stopped.compareAndSet(false, true);
    }

    public AtomicBoolean getStopped() {
        return stopped;
    }

    public void setStopped(AtomicBoolean stopped) {
        this.stopped = stopped;
    }
}
// end::class[]
