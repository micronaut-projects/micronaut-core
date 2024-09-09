package io.micronaut.http.server.tck.tests.jsonview;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.BodyAssertion;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
class JsonViewsTest {

    public static final String SPEC_NAME = "JsonViewsTest";

    private static final Map<String, Object> CONFIGURATION = Map.of("jackson.json-view.enabled", true);

    @Test
    void testJsonViewPojo() throws Exception {
        assertPath("/views/pojo");
    }

    @Test
    void testJsonViewList() throws Exception {
        assertPath("/views/list");
    }

    @Test
    void testJsonViewOptional() throws Exception {
        assertPath("/views/optional");
    }

    @Test
    void testJsonViewMono() throws Exception {
        assertPath("/views/mono");
    }

    @Test
    void testJsonViewFlux() throws Exception {
        assertPath("/views/flux");
    }

    @Test
    void testJsonViewFuture() throws Exception {
        assertPath("/views/future");
    }

    private void assertPath(String path) throws IOException {
        asserts(SPEC_NAME,
            CONFIGURATION,
            HttpRequest.GET(path),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .body(BodyAssertion.builder().body("password").doesntContain())
                .status(HttpStatus.OK)
                .build()));
    }

}
