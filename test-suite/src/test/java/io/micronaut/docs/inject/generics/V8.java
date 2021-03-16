package io.micronaut.docs.inject.generics;

// tag::class[]
public class V8 implements CylinderProvider {
    @Override
    public int getCylinders() {
        return 8;
    }
}
// end::class[]
