package io.micronaut.inject.foreach;

public class NonBeanClass {

    private int port;

    NonBeanClass(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }
}
