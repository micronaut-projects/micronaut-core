package io.micronaut.inject.generics;

public class V8 implements CylinderProvider {
    @Override
    public int getCylinders() {
        return 8;
    }
}
