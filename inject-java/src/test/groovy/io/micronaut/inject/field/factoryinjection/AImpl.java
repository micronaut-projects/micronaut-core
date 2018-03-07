package io.micronaut.inject.field.factoryinjection;

public class AImpl implements A {
    final C c;
    final C c2;

    public AImpl(C c, C c2) {
        this.c = c;
        this.c2 = c2;
    }
}
