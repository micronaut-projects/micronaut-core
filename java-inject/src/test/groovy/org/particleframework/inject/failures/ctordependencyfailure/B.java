package org.particleframework.inject.failures.ctordependencyfailure;

import javax.inject.Inject;

public class B {
    private final A a;

    @Inject
    public B(A a) {
        this.a = a;
    }
}
