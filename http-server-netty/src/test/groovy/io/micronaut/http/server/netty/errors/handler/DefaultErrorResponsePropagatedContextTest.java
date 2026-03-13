package io.micronaut.http.server.netty.errors.handler;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.propagation.slf4j.MdcPropagationContext;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.server.RouteExecutor;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN;

class DefaultErrorResponsePropagatedContextTest {
    private static final String SPEC_NAME = "DefaultErrorResponsePropagatedContextTest";

    private final EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class,
        Map.of("spec.name", SPEC_NAME)
    );

    private final HttpClient httpClient = embeddedServer.getApplicationContext().createBean(HttpClient.class, embeddedServer.getURL());

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void attachAppender() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RouteExecutor.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void detachAppender() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RouteExecutor.class);
        logger.detachAppender(listAppender);
        listAppender.stop();
        httpClient.close();
        embeddedServer.close();
    }

    @Test
    void defaultErrorLoggingIncludesMdcFromPropagatedContext() {
        HttpResponse<?> response;

        try {
            response = httpClient.toBlocking().exchange(HttpRequest.GET("/default-error"), String.class);
        } catch (HttpClientResponseException e) {
            response = e.getResponse();
        }

        Assertions.assertEquals(500, response.code());
        Assertions.assertFalse(listAppender.list.isEmpty());
        Assertions.assertTrue(listAppender.list.stream().anyMatch(event ->
            event.getFormattedMessage().contains("Unexpected error occurred: boom")
        ));
        Assertions.assertTrue(listAppender.list.stream().anyMatch(event ->
            "1234".equals(event.getMDCPropertyMap().get("trace"))
        ), () -> "MDC maps were: " + listAppender.list.stream().map(ILoggingEvent::getMDCPropertyMap).toList());
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/default-error")
    static final class DefaultErrorController {
        @Get
        String error() {
            throw new RuntimeException("boom");
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerFilter(MATCH_ALL_PATTERN)
    static final class TestMdcFilter {
        @RequestFilter
        void requestFilter(MutablePropagatedContext mutablePropagatedContext) {
            MDC.put("trace", "1234");
            mutablePropagatedContext.add(new MdcPropagationContext());
        }

        @ResponseFilter
        void clearMdc() {
            MDC.remove("trace");
        }
    }
}
