package io.micronaut.http.client.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.AsyncRawHttpClient;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An exchange that could not open a connection hands its unread body back when it asks for it,
 * and closes it otherwise.
 */
class RawUnsentBodyTest {
    private static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    private static CloseableByteBody streamed(AtomicBoolean cancelled) {
        return FACTORY.adapt(Flux.just("abc", "def")
            .map(text -> (ReadBuffer) ReadBufferFactory.getJdkFactory().copyOf(text, StandardCharsets.UTF_8))
            .doOnCancel(() -> cancelled.set(true)));
    }

    private static UnprocessedRequestException exchange(AsyncRawHttpClient client, CloseableByteBody body, RawRequestOptions options) {
        HttpRequest<?> request = HttpRequest.create(HttpMethod.POST, "http://127.0.0.1:" + SocketUtils.findAvailableTcpPort() + "/unsent");
        ExecutionException e = Assertions.assertThrows(ExecutionException.class,
            () -> client.exchange(request, body, options).toCompletableFuture().get(10, TimeUnit.SECONDS));
        return Assertions.assertInstanceOf(UnprocessedRequestException.class, e.getCause());
    }

    @Test
    void aBodyThatWasNeverReadIsHandedBack() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            AtomicBoolean cancelled = new AtomicBoolean();
            UnprocessedRequestException e = exchange(client.toAsyncRaw(), streamed(cancelled), RawRequestOptions.proxy().toBuilder().returnUnsentBody(true).build());
            Assertions.assertTrue(e.isBodyUntouched(), e.getReason().toString());
            Optional<CloseableByteBody> unsent = e.takeUnsentBody();
            Assertions.assertTrue(unsent.isPresent());
            Assertions.assertTrue(e.takeUnsentBody().isEmpty(), "taken once");
            try (CloseableAvailableByteBody bytes = unsent.get().buffer().get(5, TimeUnit.SECONDS)) {
                Assertions.assertEquals("abcdef", bytes.toString(StandardCharsets.UTF_8));
            }
            Assertions.assertFalse(cancelled.get());
        }
    }

    @Test
    void withoutTheOptionTheBodyIsClosed() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            AtomicBoolean cancelled = new AtomicBoolean();
            UnprocessedRequestException e = exchange(client.toAsyncRaw(), streamed(cancelled), RawRequestOptions.proxy());
            Assertions.assertTrue(e.takeUnsentBody().isEmpty());
        }
    }

    @Test
    void aBodySentBeforeARedirectIsNotHandedBack() throws Exception {
        int closedPort = SocketUtils.findAvailableTcpPort();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             ApplicationContext ctx = ApplicationContext.run();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            Thread redirector = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    // read the request with its body, then redirect to a port nobody listens on
                    socket.getInputStream().readNBytes(1);
                    Thread.sleep(200);
                    socket.getOutputStream().write(("HTTP/1.1 307 Temporary Redirect\r\nLocation: http://127.0.0.1:" + closedPort + "/unsent\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    Thread.sleep(200);
                } catch (Exception ignored) {
                }
            });
            redirector.start();
            HttpRequest<?> request = HttpRequest.create(HttpMethod.POST, "http://127.0.0.1:" + server.getLocalPort() + "/unsent");
            ExecutionException e = Assertions.assertThrows(ExecutionException.class,
                () -> client.toAsyncRaw().exchange(request, streamed(new AtomicBoolean()), RawRequestOptions.builder().returnUnsentBody(true).build()).toCompletableFuture().get(10, TimeUnit.SECONDS));
            UnprocessedRequestException unprocessed = Assertions.assertInstanceOf(UnprocessedRequestException.class, e.getCause());
            Assertions.assertFalse(unprocessed.isBodyUntouched());
            Assertions.assertTrue(unprocessed.takeUnsentBody().isEmpty(), "the body went to the first server");
            redirector.join();
        }
    }

    @Test
    void aBodyAClientFilterReplacedIsNotHandedBack() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", "RawUnsentBodyTest"));
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            UnprocessedRequestException e = exchange(client.toAsyncRaw(), streamed(new AtomicBoolean()), RawRequestOptions.proxy().toBuilder().returnUnsentBody(true).build());
            // the filter released the original bytes: there is nothing to hand back
            Assertions.assertFalse(e.isBodyUntouched());
            Assertions.assertTrue(e.takeUnsentBody().isEmpty());
        }
    }

    @ClientFilter("/unsent")
    @Requires(property = "spec.name", value = "RawUnsentBodyTest")
    static class ReplacingFilter {
        @RequestFilter
        void replace(MutableHttpRequest<?> request) {
            request.body("replacement");
        }
    }
}
