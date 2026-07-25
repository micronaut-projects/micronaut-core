package io.micronaut.testsuite.jpms;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.jdk.DefaultJdkHttpClientRegistry;
import io.micronaut.http.server.netty.NettyEmbeddedServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JdkHttpClientSmokeTest {

    @Test
    void startsEmbeddedServerAndCallsViaJdkHttpClient() {
        try (ApplicationContext context = ApplicationContext.run();
             NettyEmbeddedServer server = context.getBean(NettyEmbeddedServer.class).start()) {
            DefaultJdkHttpClientRegistry registry = context.getBean(DefaultJdkHttpClientRegistry.class);

            try (HttpClient client = registry.getClient(AnnotationMetadata.EMPTY_METADATA)) {
                assertNotNull(client);
                assertEquals("io.micronaut.http.client.jdk", client.getClass().getModule().getName());
            }
        }
    }
}
