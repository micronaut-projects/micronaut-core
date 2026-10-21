package io.micronaut.http.client.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * A relayed {@code 101} waits for the response of an earlier pipelined request on the same
 * connection.
 */
class UpgradeRelayAfterPipelinedRequestTest {
    private static final String SPEC_NAME = "UpgradeRelayAfterPipelinedRequestTest";

    @Test
    void earlierPipelinedResponseComesBeforeTheSwitch() throws Exception {
        try (ServerSocket upstream = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Thread t = new Thread(() -> {
                try (Socket s = upstream.accept()) {
                    InputStream in = s.getInputStream();
                    byte[] buf = new byte[4096];
                    in.read(buf);
                    OutputStream out = s.getOutputStream();
                    out.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: echo\r\nConnection: Upgrade\r\n\r\nTUNNEL-HELLO".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    while (in.read(buf) != -1) {
                        // drain
                    }
                } catch (Exception ignored) {
                }
            });
            t.setDaemon(true);
            t.start();
            try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC_NAME))) {
                server.getApplicationContext().getBean(Upstream.class).uri = URI.create("http://127.0.0.1:" + upstream.getLocalPort() + "/");
                try (Socket socket = new Socket("localhost", server.getPort())) {
                    socket.setSoTimeout(2500);
                    socket.getOutputStream().write((
                        "GET /pipelined-slow HTTP/1.1\r\nHost: localhost\r\n\r\n" +
                            "GET /pipelined-relay HTTP/1.1\r\nHost: localhost\r\nConnection: Upgrade\r\nUpgrade: echo\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    ByteArrayOutputStream received = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    try {
                        int n;
                        while ((n = socket.getInputStream().read(buf)) != -1) {
                            received.write(buf, 0, n);
                        }
                    } catch (SocketTimeoutException ignored) {
                    }
                    String text = received.toString(StandardCharsets.ISO_8859_1);
                    Assertions.assertTrue(text.startsWith("HTTP/1.1 200 "), "first bytes on the connection: " + text);
                    Assertions.assertTrue(text.contains("SLOW-BODY"), text);
                    int switched = text.indexOf("HTTP/1.1 101 ");
                    Assertions.assertTrue(switched > text.indexOf("SLOW-BODY"), text);
                    Assertions.assertTrue(text.indexOf("TUNNEL-HELLO") > switched, text);
                }
            }
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Upstream {
        volatile URI uri;
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {
        private final RawHttpClient client;
        private final Upstream upstream;

        Routes(RawHttpClient client, Upstream upstream) {
            this.client = client;
            this.upstream = upstream;
        }

        @Get(value = "/pipelined-slow", produces = MediaType.TEXT_PLAIN)
        Mono<String> slow() {
            return Mono.delay(Duration.ofSeconds(1)).map(i -> "SLOW-BODY");
        }

        @Get("/pipelined-relay")
        @Produces(MediaType.ALL)
        Mono<HttpResponse<?>> relay(ServerHttpRequest<?> request) {
            MutableHttpRequest<Object> outbound = HttpRequest.create(request.getMethod(), upstream.uri.resolve("/echo").toString());
            request.getHeaders().forEach((name, values) -> values.forEach(value -> outbound.header(name, value)));
            return Mono.from(client.exchange(outbound, request.byteBody().move(), null, RawRequestOptions.proxy()));
        }
    }
}
