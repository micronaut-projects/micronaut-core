package io.micronaut.reflection;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A bean without a scope annotation: a new instance per injection.
 */
public class Prototype {

    static final AtomicInteger CREATED = new AtomicInteger();

    public Prototype() {
        CREATED.incrementAndGet();
    }
}
