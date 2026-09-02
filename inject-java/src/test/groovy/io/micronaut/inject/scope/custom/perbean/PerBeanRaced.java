package io.micronaut.inject.scope.custom.perbean;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A bean many threads resolve at once; its construction is slow enough for them to pile up.
 */
@PerBeanScope
public class PerBeanRaced {

    public static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

    public PerBeanRaced() throws InterruptedException {
        CONSTRUCTIONS.incrementAndGet();
        Thread.sleep(100);
    }
}
