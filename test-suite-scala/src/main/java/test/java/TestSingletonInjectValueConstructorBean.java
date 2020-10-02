package test.java;

import io.micronaut.context.annotation.Value;

import javax.inject.Singleton;

@Singleton
public class TestSingletonInjectValueConstructorBean {
    private final String host;
    private final int port;

    public TestSingletonInjectValueConstructorBean(
            @Value("injected String") String host,
            @Value("42") int port
    ) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
