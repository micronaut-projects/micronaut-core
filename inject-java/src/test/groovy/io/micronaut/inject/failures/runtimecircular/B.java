package io.micronaut.inject.failures.runtimecircular;

public class B {

    final A a;

    public B(A a) {
        this.a = a;
    }

}
