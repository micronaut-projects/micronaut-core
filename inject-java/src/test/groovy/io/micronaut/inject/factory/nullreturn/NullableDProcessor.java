package io.micronaut.inject.factory.nullreturn;

import io.micronaut.context.annotation.EachBean;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

@EachBean(D.class)
public class NullableDProcessor {

    static AtomicInteger constructed = new AtomicInteger();

    private final D d;

    NullableDProcessor(@Nullable D d) {
        this.d = d;
        constructed.incrementAndGet();
    }
}