package io.micronaut.inject.field.factoryinjection;

import javax.inject.Inject;

public class B {
    @Inject
    private A a;

    public A getA() {
        return this.a;
    }
}
