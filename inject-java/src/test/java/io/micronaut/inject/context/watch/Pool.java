package io.micronaut.inject.context.watch;

import io.micronaut.context.watch.BeanWatch;

import java.util.concurrent.atomic.AtomicInteger;

public class Pool {
    public static final AtomicInteger CREATED = new AtomicInteger();
    public final int number = CREATED.incrementAndGet();
    public BeanWatch watch;
    public int applied;
}
