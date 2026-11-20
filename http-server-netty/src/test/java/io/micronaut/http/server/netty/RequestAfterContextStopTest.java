package io.micronaut.http.server.netty;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.runtime.server.EmbeddedServer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A request still in flight when its context stops, such as one a filter held, is answered with a 503 rather than
 * failing again, with a null pointer, while the server resolves an exception handler from the stopped context.
 */
class RequestAfterContextStopTest {
    static final String SPEC = "RequestAfterContextStopTest";

    @Test
    void aRequestResumedAfterTheContextStoppedIsAnswered503WithoutAnError() throws Exception {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.addAppender(appender);
        try {
            ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC));
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            int port = server.getPort();
            ParkingFilter filter = ctx.getBean(ParkingFilter.class);
            String[] reply = new String[1];
            Thread client = new Thread(() -> {
                try (Socket socket = new Socket("localhost", port)) {
                    socket.getOutputStream().write("GET /after-stop HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    reply[0] = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    reply[0] = e.toString();
                }
            });
            client.start();
            assertTrue(filter.entered.await(10, TimeUnit.SECONDS));
            ctx.stop();
            filter.release.complete(null);
            client.join(10_000);
            // the request fails on the stopped context: no exception handler is resolved from it, nothing logs an error
            assertTrue(filter.completed.await(10, TimeUnit.SECONDS));
            assertEquals(503, filter.status.get());
            List<String> errors = appender.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN) || event.getThrowableProxy() != null)
                .map(event -> event.getLoggerName() + ": " + event.getFormattedMessage())
                .toList();
            assertEquals(List.of(), errors);
        } finally {
            root.detachAppender(appender);
        }
    }

    @ServerFilter("/after-stop")
    @Requires(property = "spec.name", value = SPEC)
    static class ParkingFilter {
        final CountDownLatch entered = new CountDownLatch(1);
        final CompletableFuture<Void> release = new CompletableFuture<>();
        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicInteger status = new AtomicInteger();

        @RequestFilter
        CompletableFuture<@Nullable HttpResponse<?>> park(HttpRequest<?> request) {
            entered.countDown();
            return release.thenApply(ignored -> null);
        }

        @ResponseFilter
        void done(MutableHttpResponse<?> response) {
            status.set(response.code());
            completed.countDown();
        }
    }

    @Controller("/after-stop")
    @Requires(property = "spec.name", value = SPEC)
    static class AfterStopController {
        @Get
        @Produces("text/plain")
        String get() {
            return "served";
        }
    }
}
