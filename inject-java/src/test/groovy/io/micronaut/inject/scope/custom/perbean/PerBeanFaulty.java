package io.micronaut.inject.scope.custom.perbean;

import java.util.concurrent.atomic.AtomicInteger;

@PerBeanScope
public class PerBeanFaulty {

    public static final AtomicInteger ATTEMPTS = new AtomicInteger();

    public PerBeanFaulty() {
        ATTEMPTS.incrementAndGet();
        throw new RuntimeException("Bad things");
    }
}
