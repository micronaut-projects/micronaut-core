package io.micronaut.inject.generics;

public class V10 implements CylinderProvider{
    @Override
    public int getCylinders() {
        return 10;
    }
}
