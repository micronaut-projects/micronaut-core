package io.micronaut.inject.method.simpleinjection;

import javax.inject.Inject;

public class B {

    private A a;

    @Inject
    public void setA(A a) {
        this.a = a;
    }

    public A getA() {
        return a;
    }
}
