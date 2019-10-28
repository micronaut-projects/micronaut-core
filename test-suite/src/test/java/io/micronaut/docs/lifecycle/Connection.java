package io.micronaut.docs.lifecycle;

// tag::class[]
import java.util.concurrent.atomic.AtomicBoolean;

public class Connection {

    AtomicBoolean stopped = new AtomicBoolean(false);

    public void stop() { // <2>
        stopped.compareAndSet(false, true);
    }

}
// end::class[]
