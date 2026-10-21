package io.micronaut.http.server.netty;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A response that closes the connection before the server read the whole request body: the
 * server reads and drops the rest of the body before it closes, so that the transport does not
 * reset the connection under the response.
 */
class CloseWithUnreadBodyTest {
    private static final String SPEC_NAME = "CloseWithUnreadBodyTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            "micronaut.server.max-request-buffer-size", "1KB"
        ));
        server = ctx.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1025, 4005, 65536})
    void aBodyLargerThanTheServerBufferIsNotResetUnderTheResponse(int size) throws Exception {
        for (int i = 0; i < 200; i++) {
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                socket.setSoTimeout(10_000);
                OutputStream out = socket.getOutputStream();
                out.write(head(size));
                out.write("x".repeat(size).getBytes(StandardCharsets.US_ASCII));
                out.flush();
                String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(response.startsWith("HTTP/1.1 200"), response);
                assertTrue(response.endsWith("plain"), response);
            }
        }
    }

    @Test
    void aClientThatDoesNotSendTheRestOfTheBodyGetsTheResponseAndTheConnectionCloses() throws Exception {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            out.write(head(100_000));
            out.write("x".repeat(4000).getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(response.startsWith("HTTP/1.1 200"), response);
        }
    }

    @Test
    void theServerKeepsServing() throws IOException {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(head(5));
            out.write("hello".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("plain", response.substring(response.indexOf("\r\n\r\n") + 4));
        }
    }

    private static byte[] head(int contentLength) {
        return ("POST /cap/plain HTTP/1.1\r\n"
            + "Host: " + server.getHost() + "\r\n"
            + "Content-Type: text/plain\r\n"
            + "Connection: close\r\n"
            + "Content-Length: " + contentLength + "\r\n"
            + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.POST("/cap/plain")
                .consumes(MediaType.TEXT_PLAIN_TYPE)
                .body().handleAsync((request, pathVariables, body) -> body.discardBody().thenApply(done -> HttpResponse.ok("plain")));
        }
    }
}
