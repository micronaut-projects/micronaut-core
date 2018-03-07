package io.micronaut.inject.constructor.factoryinjection;

import io.micronaut.context.annotation.Provided;
import io.micronaut.context.annotation.Provided;

import javax.inject.Inject;

@Provided
public class AImpl implements A {
    final C c;
    final C c2;
    @Inject
    protected D d;

    public AImpl(C c, C c2) {
        this.c = c;
        this.c2 = c2;
    }
}
