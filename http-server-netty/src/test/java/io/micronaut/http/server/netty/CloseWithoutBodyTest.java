package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A response that closes the connection to a request without a body has nothing left to read
 * from the client, so the connection is closed directly. The graceful path that shuts down the
 * output and keeps reading is for requests whose body is still coming, see
 * {@link CloseWithUnreadBodyTest}. Either way the client has to receive the whole response.
 */
class CloseWithoutBodyTest {
    private static final String SPEC_NAME = "CloseWithoutBodyTest";

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

    @Test
    void aRequestWithoutBodyIsAnsweredAndTheConnectionIsClosedDirectly() throws Exception {
        for (int i = 0; i < 50; i++) {
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                socket.setSoTimeout(10_000);
                OutputStream out = socket.getOutputStream();
                out.write(("GET /no-body/plain HTTP/1.1\r\n"
                    + "Host: " + server.getHost() + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n").getBytes(StandardCharsets.US_ASCII));
                out.flush();
                String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(response.startsWith("HTTP/1.1 200"), response);
                assertTrue(response.endsWith("plain"), response);
            }
        }
    }

    @Test
    void bytesOfAnotherRequestAfterTheClosingRequestDoNotResetTheConnectionUnderTheResponse() throws Exception {
        for (int i = 0; i < 50; i++) {
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                socket.setSoTimeout(10_000);
                OutputStream out = socket.getOutputStream();
                String request = "GET /no-body/plain HTTP/1.1\r\n"
                    + "Host: " + server.getHost() + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";
                // the closing request and the start of another one arrive in the same write
                out.write((request + request.replace("Connection: close", "Connection: keep-alive")).getBytes(StandardCharsets.US_ASCII));
                out.flush();
                String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(response.startsWith("HTTP/1.1 200"), response);
                assertTrue(response.contains("plain"), response);
            }
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/no-body/plain")
                .body().handle((request, pathVariables, body) -> HttpResponse.ok("plain").contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }
}
