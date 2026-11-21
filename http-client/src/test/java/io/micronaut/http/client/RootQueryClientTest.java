package io.micronaut.http.client;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A declarative client calling a root-mounted endpoint with query parameters
 * must send "/" as the request-target path (RFC 9112 §3.2.1), not a
 * path-less "?q=x" target, which the server rejects with 400 Malformed URI.
 */
class RootQueryClientTest {
    private static final String SPEC = "RootQueryClientTest";

    @Test
    void queryOnlyClientRequestToRootController() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class,
                Map.of("spec.name", SPEC));
             ApplicationContext clientCtx = ApplicationContext.run(Map.of(
                "spec.name", SPEC,
                // no path, no trailing slash
                "root.url", "http://localhost:" + server.getPort()))) {
            RootClient client = clientCtx.getBean(RootClient.class);
            assertEquals("q=x", client.withTemplate("x"));
            assertEquals("q=x", client.withoutTemplate("x"));
            // the raw client must normalize a query-only URI the same way
            try (BlockingHttpClient rawClient = server.getApplicationContext().createBean(BlockingHttpClient.class, server.getURL())) {
                assertEquals("q=x", rawClient.retrieve(HttpRequest.GET("?q=x"), String.class));
            }
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller
    static class RootController {
        @Get
        String list(@Nullable @QueryValue String q) {
            return "q=" + q;
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Client("${root.url}")
    interface RootClient {
        @Get("{?q}")
        String withTemplate(@QueryValue String q);

        @Get
        String withoutTemplate(@QueryValue String q);
    }
}
