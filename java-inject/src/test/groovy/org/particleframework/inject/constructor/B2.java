package org.particleframework.inject.constructor;

import javax.inject.Inject;

public class B2 {
    private A a;
    private A a2;

    @Inject
    public B2(A a, A a2) {
        this.a = a;
        this.a2 = a2;
    }

    public A getA() {
        return this.a;
    }

    public A getA2() {
        return a2;
    }
}
