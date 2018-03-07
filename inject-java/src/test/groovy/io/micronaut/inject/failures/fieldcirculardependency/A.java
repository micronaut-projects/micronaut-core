package io.micronaut.inject.failures.fieldcirculardependency;

import javax.inject.Singleton;

@Singleton
public class A {
    public A(C c) {}
}
