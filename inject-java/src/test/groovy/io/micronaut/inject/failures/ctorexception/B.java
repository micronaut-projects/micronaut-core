package io.micronaut.inject.failures.ctorexception;

import javax.inject.Inject;

public class B {
    @Inject
    private A a;

    public A getA() {
        return this.a;
    }
}
