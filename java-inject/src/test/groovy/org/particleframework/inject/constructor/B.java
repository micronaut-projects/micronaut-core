package org.particleframework.inject.constructor;

import javax.inject.Inject;

public class B {
    private A a;

    @Inject
    public B(A a) {
        this.a = a;
    }

    public A getA() {
        return this.a;
    }
}
