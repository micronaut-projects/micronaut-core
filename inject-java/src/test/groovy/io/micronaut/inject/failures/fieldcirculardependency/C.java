package io.micronaut.inject.failures.fieldcirculardependency;

import javax.inject.Inject;

public class C {
    @Inject
    protected B b;
}
