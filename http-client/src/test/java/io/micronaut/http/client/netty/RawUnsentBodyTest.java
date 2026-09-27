package io.micronaut.http.client.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.http.HttpMethod;
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

import java.nio.charset.StandardCharsets;
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
}
