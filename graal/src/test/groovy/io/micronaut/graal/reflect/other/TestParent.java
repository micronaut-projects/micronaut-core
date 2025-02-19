package io.micronaut.graal.reflect.other;

import jakarta.inject.Inject;

public abstract class TestParent {
    @Inject
    String packagePrivateField;

    @Inject
    private String privateField;

    @Inject
    void packagePrivateMethod(String whatever) {};

    @Inject
    private void privateMethod(String whatever) {};
}
