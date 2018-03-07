package io.micronaut.inject.failures.ctorexception;

import javax.inject.Singleton;

@Singleton
public class A {
    public A(C c) {
    }
}
