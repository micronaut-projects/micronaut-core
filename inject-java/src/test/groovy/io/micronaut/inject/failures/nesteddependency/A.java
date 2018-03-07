package io.micronaut.inject.failures.nesteddependency;

import javax.inject.Singleton;

@Singleton
public class A {
    public A(C c) {

    }
}
