package io.micronaut.inject.foreach;

public class NonBeanClassWithPrimary {

    private int port;

    NonBeanClassWithPrimary(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }
}
