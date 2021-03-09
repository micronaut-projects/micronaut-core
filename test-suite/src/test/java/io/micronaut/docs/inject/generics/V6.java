package io.micronaut.docs.inject.generics;

public class V6 implements CylinderProvider {
    @Override
    public int getCylinders() {
        return 7;
    }
}
