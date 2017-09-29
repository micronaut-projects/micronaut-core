package org.particleframework.inject.constructor.simpleinjection;

import javax.inject.Inject;

public class B {
    private A a;

    @Inject
    B(A a) {
        this.a = a;
    }

    public A getA() {
        return this.a;
    }
}
