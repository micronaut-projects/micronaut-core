package org.particleframework.inject.failures.fielddependencymissing;

import javax.inject.Inject;

public class B {
    @Inject
    private A a;

    public A getA() {
        return this.a;
    }
}
