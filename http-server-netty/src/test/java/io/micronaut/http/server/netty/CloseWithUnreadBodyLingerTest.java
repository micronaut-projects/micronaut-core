package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A response that closes the connection before the client sent the whole request body, with a
 * client that never sends the rest: the server closes the connection when the linger time is
 * over, and over TLS it shuts down its output after the close_notify.
 */
class CloseWithUnreadBodyLingerTest {
    private static final String SPEC_NAME = "CloseWithUnreadBodyLingerTest";

    @Test
    void theServerClosesTheConnectionWhenTheLingerTimeIsOver() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.port", -1,
            "micronaut.server.max-request-buffer-size", "1KB"
        ))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (Socket socket = new Socket(server.getHost(), server.getPort())) {
                socket.setSoTimeout(30_000);
                OutputStream out = socket.getOutputStream();
                out.write(head(server, 100_000));
                out.write("x".repeat(4000).getBytes(StandardCharsets.US_ASCII));
                out.flush();
                String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(response.startsWith("HTTP/1.1 200"), response);
                // the server reads and drops what is written until it closes, then the writes fail
                assertThrows(IOException.class, () -> {
                    long deadline = System.nanoTime() + 30_000_000_000L;
                    while (System.nanoTime() < deadline) {
                        out.write('x');
                        out.flush();
                        Thread.sleep(50);
                    }
                });
            }
        }
    }

    @Test
    void overTlsTheServerShutsDownItsOutputAfterTheResponse() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.server.max-request-buffer-size", "1KB",
            "micronaut.server.ssl.enabled", true,
            "micronaut.server.ssl.build-self-signed", true,
            "micronaut.server.ssl.port", -1
        ))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, InsecureTrustManagerFactory.INSTANCE.getTrustManagers(), null);
            try (SSLSocket socket = (SSLSocket) ssl.getSocketFactory().createSocket(server.getHost(), server.getPort())) {
                socket.setSoTimeout(30_000);
                OutputStream out = socket.getOutputStream();
                out.write(head(server, 100_000));
                out.write("x".repeat(4000).getBytes(StandardCharsets.US_ASCII));
                out.flush();
                InputStream in = socket.getInputStream();
                String response = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(response.startsWith("HTTP/1.1 200"), response);
                assertTrue(response.endsWith("plain"), response);
            }
        }
    }

    private static byte[] head(EmbeddedServer server, int contentLength) {
        return ("POST /linger/plain HTTP/1.1\r\n"
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
            routes.POST("/linger/plain")
                .consumes(MediaType.TEXT_PLAIN_TYPE)
                .body().handleAsync((request, pathVariables, body) -> body.discardBody().thenApply(done -> HttpResponse.ok("plain")));
        }
    }
}
