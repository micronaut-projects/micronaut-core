package org.particleframework.inject.method.optionalinjection;

import javax.inject.Inject;
import java.util.Optional;

public class B {
    Optional<A> a;

    Optional<C> c;

    @Inject
    public void setA(Optional<A> a) {
        this.a = a;
    }

    @Inject
    public void setC(Optional<C> c) {
        this.c = c;
    }

    A getA() {
        return this.a.get();
    }

    Optional<C> getC() {
        return c;
    }
}
