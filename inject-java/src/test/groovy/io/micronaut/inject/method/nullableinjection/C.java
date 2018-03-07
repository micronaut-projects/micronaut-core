package io.micronaut.inject.method.nullableinjection;

import javax.inject.Inject;

public class C {

    A a;

    @Inject
    public void setA(A a) {
        this.a = a;
    }

    A getA() {
        return this.a;
    }
}
