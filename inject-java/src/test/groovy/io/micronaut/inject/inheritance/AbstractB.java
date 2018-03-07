package io.micronaut.inject.inheritance;

import javax.inject.Inject;

public abstract class AbstractB {
    // inject via field
    @Inject
    protected A a;

    private A another;

    private A packagePrivate;

    // inject via method
    @Inject
    public void setAnother(A a) {
        this.another = a;
    }

    // inject via package private method
    @Inject
    void setPackagePrivate(A a) {
        this.packagePrivate = a;
    }

    public A getA() {
        return a;
    }

    public A getAnother() {
        return another;
    }

    A getPackagePrivate() {
        return packagePrivate;
    }
}
