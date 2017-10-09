package org.particleframework.inject.value.singletonwithvalue;

import org.particleframework.context.annotation.Value;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class A {
    int fromConstructor;

    public A(
        @Value("foo.bar") int port) {
        this.fromConstructor = port;
    }

    @Value("foo.bar")
    Optional<Integer> optionalPort;

    @Value("foo.another")
    Optional<Integer> optionalPort2;

    @Value("foo.bar")
    int port;

    private int anotherPort;

    @Value("foo.bar")
    protected int fieldPort;

    @Value("default.port:9090")
    protected int defaultPort;

    @Inject
    void setAnotherPort(@Value("foo.bar") int port) {
        anotherPort = port;
    }

    int getAnotherPort() {
        return anotherPort;
    }

    int getFieldPort() {
        return fieldPort;
    }

    int getDefaultPort() {
        return defaultPort;
    }

    public int getFromConstructor() {
        return fromConstructor;
    }

    public void setFromConstructor(int fromConstructor) {
        this.fromConstructor = fromConstructor;
    }

    public Optional<Integer> getOptionalPort() {
        return optionalPort;
    }

    public void setOptionalPort(Optional<Integer> optionalPort) {
        this.optionalPort = optionalPort;
    }

    public Optional<Integer> getOptionalPort2() {
        return optionalPort2;
    }

    public void setOptionalPort2(Optional<Integer> optionalPort2) {
        this.optionalPort2 = optionalPort2;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setFieldPort(int fieldPort) {
        this.fieldPort = fieldPort;
    }

    public void setDefaultPort(int defaultPort) {
        this.defaultPort = defaultPort;
    }
}
