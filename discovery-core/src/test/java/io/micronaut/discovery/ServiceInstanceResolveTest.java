package io.micronaut.discovery;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceInstanceResolveTest {

    @Test
    void schemeRelativeUriStaysOnTheInstanceHost() {
        ServiceInstance instance = ServiceInstance.of("stock", URI.create("http://frontend:s3cret@127.0.0.1:9001"));

        URI resolved = instance.resolve(URI.create("//127.0.0.2"));

        assertEquals("127.0.0.1", resolved.getHost());
        assertEquals(9001, resolved.getPort());
    }

    @Test
    void schemeRelativeUriWithPortStaysOnTheInstanceHost() {
        ServiceInstance instance = ServiceInstance.of("stock", URI.create("http://127.0.0.1:9001"));

        URI resolved = instance.resolve(URI.create("//10.0.0.5:8500/path"));

        assertEquals("127.0.0.1", resolved.getHost());
        assertEquals(9001, resolved.getPort());
    }
}
