package io.micronaut.inject.field.optionalinjection;

import javax.inject.Inject;
import java.util.Optional;

public class B {
    @Inject
    private Optional<A> a;

    @Inject
    private Optional<C> c;

    A getA() {
        return this.a.get();
    }

    Optional<C> getC() {
        return c;
    }
}
