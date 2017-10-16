package org.particleframework.inject.field.privatewithqualifier;

import org.particleframework.inject.field.protectedwithqualifier.A;
import org.particleframework.inject.qualifiers.One;

import javax.inject.Inject;
import javax.inject.Named;

public class B {
    @Inject
    @One
    private A a;

    @Inject
    @Named("twoA")
    private A a2;

    public A getA() {
        return a;
    }

    public A getA2() {
        return a2;
    }
}
