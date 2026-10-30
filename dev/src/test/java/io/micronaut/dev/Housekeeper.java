package io.micronaut.dev;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * A parent-tier component that starts threads of its own as it is created, as a connection pool starts its housekeeper:
 * a plain daemon thread, and the worker of a scheduled executor, created from the thread that creates the component,
 * and so with its context class loader.
 */
public final class Housekeeper implements AutoCloseable {
    public static final AtomicInteger CREATED = new AtomicInteger();
    private final AtomicLong ticks = new AtomicLong();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "housekeeper-scheduler");
        thread.setDaemon(true);
        return thread;
    });
    private final Thread thread;
    private volatile Thread schedulerThread;
    private volatile boolean closed;

    Housekeeper() {
        thread = new Thread(() -> {
            while (!closed) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
            }
        }, "housekeeper");
        thread.setDaemon(true);
        thread.start();
        scheduler.scheduleAtFixedRate(() -> {
            schedulerThread = Thread.currentThread();
            ticks.incrementAndGet();
        }, 0, 20, TimeUnit.MILLISECONDS);
        CREATED.incrementAndGet();
    }

    public Thread thread() {
        return thread;
    }

    public Thread schedulerThread() {
        return schedulerThread;
    }

    public long ticks() {
        return ticks.get();
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() throws InterruptedException {
        closed = true;
        scheduler.shutdownNow();
        scheduler.awaitTermination(10, TimeUnit.SECONDS);
        thread.join(10_000);
    }
}
