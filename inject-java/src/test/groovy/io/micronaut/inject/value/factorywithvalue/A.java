package io.micronaut.inject.value.factorywithvalue;

public class A {
    int port;
    public A(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }
}
