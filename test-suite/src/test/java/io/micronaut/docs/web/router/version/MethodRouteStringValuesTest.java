package io.micronaut.docs.web.router.version;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Property(name = "spec.name", value = "MethodRouteStringValuesTest")
@MicronautTest
class MethodRouteStringValuesTest {

    @Test
    void methodRouteStringValuesMatchesGetValue(@Client("/") HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();
        assertScenario(client, UriBuilder.of("/example").path("user").build(), Collections.singletonList("isAuthenticated()"));
        assertScenario(client, UriBuilder.of("/example").path("admin").build(), Arrays.asList("ROLE_ADMIN", "ROLE_X"));
        assertScenario(client, UriBuilder.of("/foobarxxx").path("admin").build(), Collections.singletonList("ROLE_X"));
        assertScenario(client, UriBuilder.of("/foobarxxx").path("user").build(), Collections.emptyList()); // RolesAllowed is not inherited
    }

    private void assertScenario(BlockingHttpClient client, URI uri, List<String> expectedList) {
        HttpResponse<StringValuesAndGetValues> response = client.exchange(HttpRequest.GET(uri),
            StringValuesAndGetValues.class);
        StringValuesAndGetValues m = response.getBody().orElse(null);
        assertNotNull(m);
        assertNotNull(m.getValueString);
        assertNotNull(m.stringValue);
        assertEquals(expectedList.size(), m.getValueString.size());
        assertEquals(expectedList.size(), m.stringValue.size());
        for (String expected : expectedList) {
            assertTrue(m.getValueString.contains(expected));
            assertTrue(m.stringValue.contains(expected));
        }
    }

    @Property(name = "spec.name", value = "MethodRouteStringValuesTest")
    @Controller("/example")
    @RolesAllowed("isAuthenticated()")
    static class ExampleController {

        @Get("/admin")
        @RolesAllowed({"ROLE_ADMIN", "ROLE_X"})
        StringValuesAndGetValues admin(HttpRequest<?> request) {
            return stringValuesAndGetValues(request);
        }

        @Get("/user")
        StringValuesAndGetValues user(HttpRequest<?> request) {
            return stringValuesAndGetValues(request);
        }
    }

    interface ExampleInterface {
        @Get("/admin")
        @RolesAllowed("ROLE_ADMIN")
        StringValuesAndGetValues admin(HttpRequest<?> request);

        @Get("/user")
        @RolesAllowed("ROLE_USER")
        StringValuesAndGetValues user(HttpRequest<?> request);
    }

    @Controller("/foobarxxx")
    @Property(name = "spec.name", value = "MethodRouteStringValuesTest")
    static class ExampleControllerWithInterface implements ExampleInterface {
        @RolesAllowed("ROLE_X")
        @Override
        public StringValuesAndGetValues admin(HttpRequest<?> request) {
            return stringValuesAndGetValues(request);
        }

        @Override
        public StringValuesAndGetValues user(HttpRequest<?> request) {
            return stringValuesAndGetValues(request);
        }
    }

    private static StringValuesAndGetValues stringValuesAndGetValues(HttpRequest<?> request) {
        StringValuesAndGetValues defaultResult = new StringValuesAndGetValues(Collections.emptyList(), Collections.emptyList());
        return methodBasedRouteMatch(request).map(methodRoute ->
            new StringValuesAndGetValues(methodRoute.getValue(RolesAllowed.class, String[].class).map(Arrays::asList).orElse(Collections.emptyList()),
                Arrays.asList(methodRoute.stringValues(RolesAllowed.class))))
            .orElse(defaultResult);
    }

    private static Optional<MethodBasedRouteMatch> methodBasedRouteMatch(HttpRequest<?> request) {
        RouteMatch<?> routeMatch = request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class).orElse(null);
        if (routeMatch == null) {
            return Optional.empty();
        }
        if (routeMatch instanceof MethodBasedRouteMatch methodRoute) {
            return Optional.of(methodRoute);
        }

        return Optional.empty();
    }

    @JsonInclude
    @Introspected
    record StringValuesAndGetValues(List<String> getValueString, List<String> stringValue) {
    }
}
