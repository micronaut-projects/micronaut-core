package org.particleframework.inject.constructor.nullableinjection;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

public class B {
    private A a;

    @Inject
    B(@Nullable A a) {
        this.a = a;
    }

    public A getA() {
        return this.a;
    }
}
