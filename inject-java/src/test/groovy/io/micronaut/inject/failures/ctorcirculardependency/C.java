package io.micronaut.inject.failures.ctorcirculardependency;

import javax.inject.Inject;

public class C {
    @Inject
    public C(B b ) {}
}
