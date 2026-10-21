package io.micronaut.http.server.netty.binders;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * an element of a {@code @Body Flux<T>} JSON body is decoded in
 * memory and must be limited by max-request-buffer-size, like {@code AsyncRequestBody.elements}.
 */
class PublisherBodyElementLimitTest {
    private static final String SPEC = "PublisherBodyElementLimitTest";
    private static final int LIMIT = 100_000;

    @Test
    void smallElementsOfALargeBodyAreStreamed() throws Exception {
        String element = "{\"text\":\"" + "x".repeat(LIMIT / 2) + "\"}";
        assertEquals("HTTP/1.1 200 OK", post("[" + element + "," + element + "," + element + "]"));
    }

    @Test
    void anElementLargerThanTheBufferLimitIsRejected() throws Exception {
        assertEquals("HTTP/1.1 413 Request Entity Too Large", post("[{\"text\":\"" + "x".repeat(2 * LIMIT) + "\"}]"));
    }

    private static String post(String body) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC, "micronaut.server.port", -1,
            "micronaut.server.max-request-buffer-size", LIMIT, "micronaut.server.max-request-size", 10 * LIMIT))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.getPort())) {
                socket.setSoTimeout(30_000);
                socket.getOutputStream().write(("POST /element-limit/lines HTTP/1.1\r\nHost: localhost\r\nContent-Type: " + MediaType.APPLICATION_JSON + "\r\n"
                    + "Connection: close\r\nContent-Length: " + body.length() + "\r\n\r\n" + body).getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().flush();
                return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
            }
        }
    }

    @Introspected
    record Line(String text) {
    }

    @Controller("/element-limit")
    @Requires(property = "spec.name", value = SPEC)
    static class ElementLimitController {
        @Post(uri = "/lines", consumes = MediaType.APPLICATION_JSON)
        Mono<Long> lines(@Body Flux<Line> lines) {
            return lines.count();
        }
    }
}
