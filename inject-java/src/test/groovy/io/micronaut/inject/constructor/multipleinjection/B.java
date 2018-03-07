package io.micronaut.inject.constructor.multipleinjection;

import javax.inject.Inject;

public class B {
    private A a;
    private C c;

    @Inject
    public B(A a, C c) {
        this.a = a;
        this.c = c;
    }

    public A getA() {
        return this.a;
    }

    public C getC() {
        return this.c;
    }
}
