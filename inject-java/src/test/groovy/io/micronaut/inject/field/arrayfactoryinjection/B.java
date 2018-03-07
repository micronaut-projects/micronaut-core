package io.micronaut.inject.field.arrayfactoryinjection;

import javax.inject.Inject;

public class B {
    @Inject
    private A[] all;

    public A[] getAll() {
        return this.all;
    }
}
