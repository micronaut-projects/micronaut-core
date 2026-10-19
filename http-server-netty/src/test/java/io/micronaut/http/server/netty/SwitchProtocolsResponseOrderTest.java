package io.micronaut.http.server.netty;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseWrapper;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.UpgradedHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A {@code 101} relayed by a route is written after the responses of the requests pipelined
 * before it on the same connection.
 */
class SwitchProtocolsResponseOrderTest {
    private static final String SPEC_NAME = "SwitchProtocolsResponseOrderTest";
    private static final String PIPELINED = "GET /order/slow HTTP/1.1\r\nHost: localhost\r\n\r\n" +
        "GET /order/switch HTTP/1.1\r\nHost: localhost\r\nConnection: Upgrade\r\nUpgrade: echo\r\n\r\n";

    @Test
    void switchWaitsForEarlierResponse() throws Exception {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC_NAME));
             Socket socket = new Socket("localhost", server.getPort())) {
            Upgrades upgrades = server.getApplicationContext().getBean(Upgrades.class);
            upgrades.slowDelay = Duration.ofMillis(500);
            socket.setSoTimeout(2000);
            socket.getOutputStream().write(PIPELINED.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            try {
                int n;
                while ((n = socket.getInputStream().read(buf)) != -1) {
                    received.write(buf, 0, n);
                    String soFar = received.toString(StandardCharsets.ISO_8859_1);
                    int head = soFar.indexOf("HTTP/1.1 101 ");
                    if (head >= 0 && soFar.indexOf("\r\n\r\n", head) > 0) {
                        break;
                    }
                }
            } catch (SocketTimeoutException ignored) {
            }
            String text = received.toString(StandardCharsets.ISO_8859_1);
            Assertions.assertTrue(text.startsWith("HTTP/1.1 200 "), text);
            int slow = text.indexOf("SLOW-BODY");
            int switched = text.indexOf("HTTP/1.1 101 ");
            Assertions.assertTrue(slow > 0, text);
            Assertions.assertTrue(switched > slow, text);
            // the connection is handed to the upgraded response
            Assertions.assertTrue(upgrades.sent.await(10, TimeUnit.SECONDS));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Upgrades {
        volatile Duration slowDelay = Duration.ZERO;
        final CountDownLatch sent = new CountDownLatch(1);
    }

    @Controller("/order")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {
        private final Upgrades upgrades;

        Routes(Upgrades upgrades) {
            this.upgrades = upgrades;
        }

        @Get(value = "/slow", produces = MediaType.TEXT_PLAIN)
        Mono<String> slow() {
            return Mono.delay(upgrades.slowDelay).map(i -> "SLOW-BODY");
        }

        @Get("/switch")
        @Produces(MediaType.ALL)
        HttpResponse<?> switchProtocols() {
            return new EchoUpgrade(upgrades);
        }
    }

    static final class EchoUpgrade extends HttpResponseWrapper<Object> implements UpgradedHttpResponse<Object> {
        private final Upgrades upgrades;
        private final CloseableByteBody body = ByteBodyFactory.createDefault(NettyByteBufferFactory.DEFAULT)
            .adapt("UPGRADED".getBytes(StandardCharsets.US_ASCII));

        EchoUpgrade(Upgrades upgrades) {
            super(HttpResponse.status(HttpStatus.SWITCHING_PROTOCOLS)
                .header(HttpHeaders.UPGRADE, "echo")
                .header(HttpHeaders.CONNECTION, "Upgrade"));
            this.upgrades = upgrades;
        }

        @Override
        public void send(CloseableByteBody outbound) {
            upgrades.sent.countDown();
            outbound.close();
        }

        @Override
        public @Nullable String getProtocol() {
            return "echo";
        }

        @Override
        public ByteBody byteBody() {
            return body;
        }

        @Override
        public void close() {
            body.close();
        }
    }
}
