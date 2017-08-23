package org.particleframework.inject.inheritance;

import javax.inject.Inject;

public abstract class AbstractB {
    // inject via field
    @Inject
    protected A a;
    private A another;
    // inject via method
    @Inject
    public void setAnother(A a) {
        this.another = a;
    }

    public A getA() {
        return a;
    }

    public A getAnother() {
        return another;
    }
}
