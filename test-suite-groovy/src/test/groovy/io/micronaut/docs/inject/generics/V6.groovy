package io.micronaut.docs.inject.generics

// tag::class[]
class V6 implements CylinderProvider {
    @Override
    int getCylinders() { 6 }
}
// end::class[]
