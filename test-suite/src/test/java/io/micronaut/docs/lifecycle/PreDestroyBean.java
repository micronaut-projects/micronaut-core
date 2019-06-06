package io.micronaut.docs.lifecycle;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class PreDestroyBean implements AutoCloseable {
    @PreDestroy
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

    private AtomicBoolean stopped = new AtomicBoolean(false);
}
