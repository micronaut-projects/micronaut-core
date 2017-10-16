package org.particleframework.inject.field.simpleinjection;

import javax.inject.Inject;

public class B {

    @Inject
    private A a;

    public A getA() {
        return a;
    }
}
