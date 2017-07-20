package org.particleframework.inject.constructor;

import javax.inject.Inject;

public class BWithAC {
    private A a;
    private C c;

    @Inject
    public BWithAC(A a, C c) {
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
