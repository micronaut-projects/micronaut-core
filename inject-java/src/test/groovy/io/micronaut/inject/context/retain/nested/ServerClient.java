package io.micronaut.inject.context.retain.nested;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A client of a server and its streams: expensive, and holding nothing of the context.
 */
public final class ServerClient {
    public static final AtomicInteger CREATED = new AtomicInteger();
    public static final AtomicInteger CLOSED = new AtomicInteger();
    public final List<String> streams;

    ServerClient(List<String> streams) {
        this.streams = streams;
        CREATED.incrementAndGet();
    }

    public void close() {
        CLOSED.incrementAndGet();
    }
}
