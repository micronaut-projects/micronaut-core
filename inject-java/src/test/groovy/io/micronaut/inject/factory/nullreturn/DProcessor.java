package io.micronaut.inject.factory.nullreturn;

import io.micronaut.context.annotation.EachBean;

import java.util.concurrent.atomic.AtomicInteger;

@EachBean(D.class)
public class DProcessor {

    static AtomicInteger constructed = new AtomicInteger();

    private final D d;

    DProcessor(D d) {
        this.d = d;
        constructed.incrementAndGet();
    }
}
