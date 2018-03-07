package io.micronaut.inject.constructor.nullableinjection;

import javax.inject.Inject;

public class C {

    private A a;

    @Inject
    C(A a) {
        this.a = a;
    }

    public A getA() {
        return this.a;
    }
}
