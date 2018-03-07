package io.micronaut.inject.constructor.optionalinjection;

import javax.inject.Inject;
import java.util.Optional;

public class B {
    private Optional<A> a;
    private Optional<C> c;

    @Inject
    public B(Optional<A> a, Optional<C> c) {
        this.a = a;
        this.c = c;
    }

    A getA() {
        return this.a.get();
    }

    Optional<C> getC() {
        return c;
    }
}
