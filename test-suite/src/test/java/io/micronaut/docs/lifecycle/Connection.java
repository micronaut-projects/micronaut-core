package io.micronaut.docs.lifecycle;

// tag::class[]
import java.util.concurrent.atomic.AtomicBoolean;

public class Connection {

    private AtomicBoolean stopped = new AtomicBoolean(false);

    public void stop() { // <2>
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