package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.BodyAssertion;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class RootRoutingTest {
    public static final String SPEC_NAME = "RootRoutingTest";

    @Test
    void testRouting() throws IOException {
        TestScenario.asserts(SPEC_NAME,
            HttpRequest.GET("/"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(BodyAssertion.builder().body("""
                    {"key":"hello","value":"world"}""").equals())
                .build()));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller
    @ExecuteOn(TaskExecutors.BLOCKING)
    static class MyController {

        @Post
        public KeyValue createRoot(@Body KeyValue body) {
            return body;
        }

        @Get
        public KeyValue root() {
            return new KeyValue("hello", "world");
        }

        @Get("/{id}")
        public KeyValue id(String id) {
            return new KeyValue("hello", id);
        }

        @Get("/{id}/items")
        public List<KeyValue> items(String id) {
            return List.of(new KeyValue("hello", id), new KeyValue("foo", "bar"));
        }
    }

    @Introspected
    private record KeyValue(String key, String value) {
    }

}
