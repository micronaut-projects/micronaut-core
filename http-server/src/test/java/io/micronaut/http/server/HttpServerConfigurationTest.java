package io.micronaut.http.server;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpServerConfigurationTest {

    @Test
    void defaultCorsHttpResponseHeaderValuesIsNull() {

        try (ApplicationContext ctx = ApplicationContext.run()) {
            HttpServerConfiguration serverConfiguration = ctx.getBean(HttpServerConfiguration.class);
            assertNull(serverConfiguration.getCors().getCrossOriginEmbedderPolicy());
            assertNull(serverConfiguration.getCors().getCrossOriginResourcePolicy());
        }
    }
}
