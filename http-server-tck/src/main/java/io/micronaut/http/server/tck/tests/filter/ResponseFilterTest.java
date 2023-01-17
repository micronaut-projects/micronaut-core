package io.micronaut.http.server.tck.tests.filter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import io.micronaut.http.server.tck.TestScenario;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ResponseFilterTest {
    public static final String SPEC_NAME = "ResponseFilterTest";

    @Test
    public void responseFilterImmediateRequestParameter() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/response-filter/immediate-request-parameter"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("foo")
                    .build());
                Assertions.assertEquals(
                    List.of("responseFilterImmediateRequestParameter /response-filter/immediate-request-parameter"),
                    server.getApplicationContext().getBean(MyServerFilter.class).events
                );
            })
            .run();
    }

    @Test
    public void responseFilterImmediateMutableRequestParameter() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/response-filter/immediate-mutable-request-parameter"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("foo")
                    .build());
                Assertions.assertEquals(
                    List.of("responseFilterImmediateMutableRequestParameter /response-filter/immediate-mutable-request-parameter"),
                    server.getApplicationContext().getBean(MyServerFilter.class).events
                );
            })
            .run();
    }

    @Test
    public void responseFilterResponseParameter() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/response-filter/response-parameter"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("foo")
                    .build());
                Assertions.assertEquals(
                    List.of("responseFilterResponseParameter foo"),
                    server.getApplicationContext().getBean(MyServerFilter.class).events
                );
            })
            .run();
    }

    @Test
    public void responseFilterMutableResponseParameter() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/response-filter/mutable-response-parameter"))
            .assertion((server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("responseFilterMutableResponseParameter foo")
                    .build());
            })
            .run();
    }

    @Test
    public void responseFilterThrowableParameterNotCalledForControllerError() throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.GET("/response-filter/throwable-parameter"))
            .assertion((server, request) -> {
                AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build());
                // filter not called, the error is mapped to a response before filters are invoked
                Assertions.assertEquals(
                    List.of(),
                    server.getApplicationContext().getBean(MyServerFilter.class).events
                );
            })
            .run();
    }

    @ServerFilter
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class MyServerFilter {
        List<String> events = new ArrayList<>();

        @ResponseFilter("/response-filter/immediate-request-parameter")
        public void responseFilterImmediateRequestParameter(HttpRequest<?> request) {
            events.add("responseFilterImmediateRequestParameter " + request.getPath());
        }

        @ResponseFilter("/response-filter/immediate-mutable-request-parameter")
        public void responseFilterImmediateMutableRequestParameter(MutableHttpRequest<?> request) {
            events.add("responseFilterImmediateMutableRequestParameter " + request.getPath());
        }

        @ResponseFilter("/response-filter/response-parameter")
        public void responseFilterResponseParameter(HttpResponse<?> response) {
            events.add("responseFilterResponseParameter " + response.body());
        }

        @ResponseFilter("/response-filter/mutable-response-parameter")
        public void responseFilterMutableResponseParameter(MutableHttpResponse<?> response) {
            response.body("responseFilterMutableResponseParameter " + response.body());
        }

        @ResponseFilter("/response-filter/throwable-parameter")
        public void responseFilterThrowableParameter(Throwable t) {
            events.add("responseFilterThrowableParameter " + t.getMessage());
        }
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class MyController {
        @Get("/response-filter/immediate-request-parameter")
        public String responseFilterImmediateRequestParameter() {
            return "foo";
        }

        @Get("/response-filter/immediate-mutable-request-parameter")
        public String responseFilterImmediateMutableRequestParameter() {
            return "foo";
        }

        @Get("/response-filter/response-parameter")
        public String responseFilterResponseParameter() {
            return "foo";
        }

        @Get("/response-filter/mutable-response-parameter")
        public String responseFilterMutableResponseParameter() {
            return "foo";
        }

        @Get("/response-filter/throwable-parameter")
        public String responseFilterThrowableParameter() {
            throw new RuntimeException("foo");
        }
    }
}
