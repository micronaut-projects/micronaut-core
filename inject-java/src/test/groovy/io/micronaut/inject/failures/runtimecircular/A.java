package io.micronaut.inject.failures.runtimecircular;

public class A {

    final B b;

    public A(B b) {
        this.b = b;
    }

}
