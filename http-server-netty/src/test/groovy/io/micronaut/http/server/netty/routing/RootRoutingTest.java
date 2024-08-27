package io.micronaut.http.server.netty.routing;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Property(name = "spec.name", value = "RootRoutingTest")
@MicronautTest
class RootRoutingTest {

    @Inject
    MyClient client;

    @Test
    void testRootEndpoint() {
        KeyValue kv = client.getRoot();
        Assertions.assertEquals("hello", kv.getKey());
        Assertions.assertEquals("world", kv.getValue());
    }

}
