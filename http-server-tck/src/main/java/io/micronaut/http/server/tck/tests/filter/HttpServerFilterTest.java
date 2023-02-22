package io.micronaut.http.server.tck.tests.filter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import io.micronaut.http.server.tck.ServerUnderTest;
import io.micronaut.http.server.tck.TestScenario;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HttpServerFilterTest {
    private static final String PATH = "/http-server-filter-test";
    private static final String SPEC_NAME = "HttpServerFilterTest";

    @Test
    public void httpServerFilterTest() throws IOException {
        assertion(HttpRequest.GET(PATH),
            throwsStatus(HttpStatus.UNAUTHORIZED));

        assertion(HttpRequest.GET(PATH).header(HttpHeaders.AUTHORIZATION, "ROLE_USER"),
            throwsStatus(HttpStatus.FORBIDDEN));

        BiConsumer<ServerUnderTest, HttpRequest<?>> okAssertion = (server, request) ->
            AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("foo")
                .build());

        assertion(HttpRequest.GET(PATH).header(HttpHeaders.AUTHORIZATION, "ROLE_ADMIN"),
            okAssertion);

        assertion(HttpRequest.GET("/open"),
            okAssertion);
    }

    private static BiConsumer<ServerUnderTest, HttpRequest<?>> throwsStatus(HttpStatus status) {
        return (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
            .status(status)
            .build());
    }

    private static void assertion(HttpRequest<?> request, BiConsumer<ServerUnderTest, HttpRequest<?>> assertion) throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(request)
            .assertion(assertion)
            .run();
    }

    @Filter(value = MATCH_ALL_PATTERN)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class SecurityFilter implements HttpServerFilter {

        @Override
        public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            RouteMatch<?> routeMatch = request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class).orElse(null);
            if (routeMatch instanceof MethodBasedRouteMatch) {
                MethodBasedRouteMatch<?, ?> methodRoute = ((MethodBasedRouteMatch) routeMatch);
                if (methodRoute.hasAnnotation(RolesAllowed.class)) {
                    String role = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
                    if (role == null) {
                        return Mono.fromCallable(() -> HttpResponse.status(HttpStatus.UNAUTHORIZED));
                    }
                    Optional<String[]> optionalValue = methodRoute.getValue(RolesAllowed.class, String[].class);
                    if (optionalValue.isPresent()) {
                        String[] roles = optionalValue.get();
                        if (role != null && Stream.of(roles).anyMatch(r -> r.equals(role))) {
                            return chain.proceed(request);
                        }
                    }
                    return Mono.fromCallable(() -> HttpResponse.status(HttpStatus.FORBIDDEN));
                }
            }
            return chain.proceed(request);
        }
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class MyController {
        @RolesAllowed("ROLE_ADMIN")
        @Get("/http-server-filter-test")
        public String rolesAllowed(HttpRequest<?> request) {
            return "foo";
        }

        @Get("/open")
        public String open(HttpRequest<?> request) {
            return "foo";
        }
    }

}
