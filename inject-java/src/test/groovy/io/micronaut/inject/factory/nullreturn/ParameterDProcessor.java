package io.micronaut.inject.factory.nullreturn;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;

import java.util.concurrent.atomic.AtomicInteger;

@EachBean(D.class)
public class ParameterDProcessor {

    static AtomicInteger constructed = new AtomicInteger();

    private final D d;

    ParameterDProcessor(@Parameter D d) {
        this.d = d;
        constructed.incrementAndGet();
    }
}
