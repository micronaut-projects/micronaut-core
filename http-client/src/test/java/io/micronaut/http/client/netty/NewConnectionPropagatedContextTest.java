package io.micronaut.http.client.netty;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The {@link PropagatedContext} of the caller is visible where the request is written and where
 * the response is handled, also for the first request of a new connection, whose acquisition
 * completes on the event loop of the connection.
 */
class NewConnectionPropagatedContextTest {
    private static final String LOGGER_NAME = "io.micronaut.http.client.netty.NewConnectionPropagatedContextTest.client";

    enum Mode {
        HTTP_1,
        H2C,
        H2_TLS
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void contextIsPropagatedOnNewAndPooledConnections(Mode mode) {
        Logger logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
        RecordingAppender appender = new RecordingAppender();
        appender.start();
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
        try (ApplicationContext ctx = start(mode);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
            RecordingFilter filter = ctx.getBean(RecordingFilter.class);

            // first request: the connection is opened for it
            Assertions.assertEquals("ok", exchange(client, "first"));
            // second request: the connection is reused from the pool
            Assertions.assertEquals("ok", exchange(client, "second"));

            Assertions.assertEquals(List.of("first", "second"), appender.sendingContexts, "context when the request is sent");
            Assertions.assertEquals(List.of("first", "second"), filter.responseContexts, "context in the response filter");
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }

    private static String exchange(HttpClient client, String marker) {
        return PropagatedContext.getOrEmpty().plus(new Marker(marker)).propagate(() -> {
            HttpResponse<String> response = Mono.from(client.exchange(HttpRequest.GET("/new-connection-context"), String.class)).block();
            return response.body();
        });
    }

    private static ApplicationContext start(Mode mode) {
        boolean tls = mode == Mode.H2_TLS;
        boolean http2 = mode != Mode.HTTP_1;
        return ApplicationContext.run(Map.of(
            "spec.name", "NewConnectionPropagatedContextTest",
            "micronaut.http.client.logger-name", LOGGER_NAME,
            "micronaut.http.client.ssl.insecure-trust-all-certificates", tls,
            "micronaut.http.client.alpn-modes", http2 ? "h2" : "http/1.1",
            "micronaut.http.client.plaintext-mode", http2 ? "h2c_prior_knowledge" : "http_1",
            "micronaut.server.http-version", http2 ? "2.0" : "1.1",
            "micronaut.server.ssl.enabled", tls,
            "micronaut.server.ssl.build-self-signed", true,
            "micronaut.server.ssl.port", -1
        ));
    }

    private static String currentMarker() {
        return PropagatedContext.find()
            .flatMap(ctx -> ctx.find(Marker.class))
            .map(Marker::value)
            .orElse("<none>");
    }

    record Marker(String value) implements PropagatedContextElement {
    }

    static final class RecordingAppender extends AppenderBase<ILoggingEvent> {
        final List<String> sendingContexts = new CopyOnWriteArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            if (event.getMessage().startsWith("Sending HTTP")) {
                sendingContexts.add(currentMarker());
            }
        }
    }

    @ClientFilter
    @Singleton
    @Requires(property = "spec.name", value = "NewConnectionPropagatedContextTest")
    static class RecordingFilter {
        final List<String> responseContexts = new CopyOnWriteArrayList<>();

        @ResponseFilter
        void onResponse(HttpResponse<?> response) {
            responseContexts.add(currentMarker());
        }
    }

    @Controller("/new-connection-context")
    @Requires(property = "spec.name", value = "NewConnectionPropagatedContextTest")
    static class ContextController {
        @Get(produces = MediaType.TEXT_PLAIN)
        String get() {
            return "ok";
        }
    }
}
