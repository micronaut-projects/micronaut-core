package io.micronaut.docs.inject.generics

// tag::class[]
class V8 implements CylinderProvider {
    @Override
    int getCylinders() { 8 }
}
// end::class[]
