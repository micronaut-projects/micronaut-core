package io.micronaut.docs.server.functional;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A body read by an asynchronous handler is read as the route consumes when the request has no
 * content type, like the body of a controller route.
 */
class AsyncBodyContentTypeTest {

    @Singleton
    @Requires(property = "spec.name", value = "AsyncBodyContentTypeTest")
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.POST("/declared").consumes(MediaType.APPLICATION_JSON_TYPE).body().handleAsync((request, pathVariables, body) ->
                body.body(Item.class).thenApply(item -> HttpResponse.ok("declared " + item.name()).contentType(MediaType.TEXT_PLAIN_TYPE)));
            routes.POST("/default").body().handleAsync((request, pathVariables, body) ->
                body.body(Item.class).thenApply(item -> HttpResponse.ok("default " + item.name()).contentType(MediaType.TEXT_PLAIN_TYPE)));
            routes.POST("/several").consumes(MediaType.APPLICATION_JSON_TYPE, MediaType.TEXT_PLAIN_TYPE).body().handleAsync((request, pathVariables, body) ->
                body.body(Item.class).thenApply(item -> HttpResponse.ok("several " + item.name()).contentType(MediaType.TEXT_PLAIN_TYPE)));
        }
    }

    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", "AsyncBodyContentTypeTest"));
    }

    @AfterAll
    static void stop() {
        if (server != null) {
            server.stop();
        }
    }

    /**
     * Posts a JSON body with no content type header, which an HTTP client does not do.
     *
     * @param uri The URI
     * @return The status line and the body of the answer
     */
    private static String post(String uri) throws IOException {
        byte[] body = "{\"id\":1,\"name\":\"fred\"}".getBytes(StandardCharsets.UTF_8);
        try (Socket socket = new Socket("localhost", server.getPort())) {
            OutputStream out = socket.getOutputStream();
            out.write(("POST " + uri + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\nContent-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(body);
            out.flush();
            String answer = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return answer.substring(0, answer.indexOf("\r\n")) + " | " + answer.substring(answer.indexOf("\r\n\r\n") + 4);
        }
    }

    @Test
    void theDeclaredTypeIsTheDefault() throws IOException {
        assertTrue(post("/declared").endsWith("declared fred"));
    }

    @Test
    void jsonIsTheDefaultOfARouteThatDeclaresNoType() throws IOException {
        assertTrue(post("/default").endsWith("default fred"));
    }

    @Test
    void severalTypesDoNotChooseOne() throws IOException {
        assertTrue(post("/several").startsWith("HTTP/1.1 400"));
    }
}
