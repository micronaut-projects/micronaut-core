package io.micronaut.docs.lifecycle;

import java.util.concurrent.atomic.AtomicBoolean;

public class Connection {
    public void stop() {// <2>
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
