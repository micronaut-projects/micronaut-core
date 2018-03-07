package io.micronaut.inject.value.factorywithvalue;

public class B {
    A a;
    int port;

    public B(A a, int port) {
        this.a = a;
        this.port = port;
    }

    public A getA() {
        return a;
    }

    public int getPort() {
        return port;
    }
}
