package io.micronaut.inject.method.qualifierinjection;

import io.micronaut.inject.qualifiers.One;

import javax.inject.Inject;
import javax.inject.Named;

public class B {
    private A a;
    private A a2;

    @Inject
    public void setA(@One A a) {
        this.a = a;
    }

    @Inject
    public void setAnother(@Named("twoA") A a2) {
        this.a2 = a2;
    }

    public A getA() {
        return a;
    }

    public A getA2() {
        return a2;
    }
}
