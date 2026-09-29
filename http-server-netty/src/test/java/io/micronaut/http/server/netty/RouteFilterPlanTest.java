package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.FilterPatternStyle;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The filters that apply to a request of a route, now decided per route where the route decides
 * them: path patterns, HTTP methods, {@link FilterMatcher} annotations, the regex pattern style,
 * trailing slashes and legacy filters that can be disabled.
 */
public class RouteFilterPlanTest {
    private static final String SPEC = "RouteFilterPlanTest";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC));
        server = ctx.getBean(EmbeddedServer.class).start();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stop() {
        client.close();
        server.close();
        ctx.close();
    }

    private static List<String> filters(String method, String path) throws IOException, InterruptedException {
        java.net.http.HttpResponse<String> response = client.send(
            java.net.http.HttpRequest.newBuilder(server.getURI().resolve(path))
                .method(method, java.net.http.HttpRequest.BodyPublishers.noBody())
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString()
        );
        return response.headers().allValues("X-Filter").stream().sorted().toList();
    }

    @Test
    void variableTemplate() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertEquals(List.of("A", "B", "E", "G"), filters("GET", "/plan/items/1"));
        }
    }

    @Test
    void variableTemplateWithTrailingSlash() throws Exception {
        assertEquals(List.of("A", "E", "G"), filters("GET", "/plan/items/1/"));
    }

    @Test
    void literalTemplate() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertEquals(List.of("A", "E", "F", "G", "H"), filters("GET", "/plan/static"));
        }
    }

    @Test
    void literalTemplateWithTrailingSlash() throws Exception {
        assertEquals(List.of("A", "E", "F", "G"), filters("GET", "/plan/static/"));
    }

    @Test
    void method() throws Exception {
        assertEquals(List.of("A", "B", "D", "E", "G"), filters("POST", "/plan/items/1"));
    }

    @Test
    void routeWithoutTheMatcherAnnotation() throws Exception {
        assertEquals(List.of(), filters("GET", "/other/x"));
    }

    @Test
    void disabledLegacyFilter() throws Exception {
        ToggleableFilter filter = ctx.getBean(ToggleableFilter.class);
        filter.enabled = false;
        try {
            assertEquals(List.of("A", "B", "E"), filters("GET", "/plan/items/1"));
            assertEquals(List.of("A", "E", "F", "H"), filters("GET", "/plan/static"));
        } finally {
            filter.enabled = true;
        }
        assertEquals(List.of("A", "B", "E", "G"), filters("GET", "/plan/items/1"));
    }

    @FilterMatcher
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface PlanMarker {
    }

    @Controller("/plan")
    @PlanMarker
    @Requires(property = "spec.name", value = SPEC)
    @Produces(MediaType.TEXT_PLAIN)
    static class PlanController {
        @Get("/items/{id}")
        String item(String id) {
            return id;
        }

        @Post("/items/{id}")
        String postItem(String id) {
            return id;
        }

        @Get("/static")
        String staticRoute() {
            return "static";
        }
    }

    @Controller("/other")
    @Requires(property = "spec.name", value = SPEC)
    @Produces(MediaType.TEXT_PLAIN)
    static class OtherController {
        @Get("/x")
        String x() {
            return "x";
        }
    }

    @ServerFilter("/plan/**")
    @Order(1)
    @Requires(property = "spec.name", value = SPEC)
    static class FilterA {
        @ResponseFilter
        void filter(MutableHttpResponse<?> response) {
            response.header("X-Filter", "A");
        }
    }

    @ServerFilter("/plan/items/*")
    @Order(2)
    @Requires(property = "spec.name", value = SPEC)
    static class FilterB {
        @ResponseFilter
        void filter(MutableHttpResponse<?> response) {
            response.header("X-Filter", "B");
        }
    }

    @ServerFilter("/admin/**")
    @Order(3)
    @Requires(property = "spec.name", value = SPEC)
    static class FilterC {
        @ResponseFilter
        void filter(MutableHttpResponse<?> response) {
            response.header("X-Filter", "C");
        }
    }

    @ServerFilter(value = "/plan/**", methods = HttpMethod.POST)
    @Order(4)
    @Requires(property = "spec.name", value = SPEC)
    static class FilterD {
        @ResponseFilter
        void filter(MutableHttpResponse<?> response) {
            response.header("X-Filter", "D");
        }
    }

    @PlanMarker
    @ServerFilter(Filter.MATCH_ALL_PATTERN)
    @Order(5)
    @Requires(property = "spec.name", value = SPEC)
    static class FilterE {
        @ResponseFilter
        void filter(MutableHttpResponse<?> response) {
            response.header("X-Filter", "E");
        }
    }

    @ServerFilter(value = "/plan/stat.*", patternStyle = FilterPatternStyle.REGEX)
    @Order(6)
    @Requires(property = "spec.name", value = SPEC)
    static class FilterF {
        @ResponseFilter
        void filter(MutableHttpResponse<?> response) {
            response.header("X-Filter", "F");
        }
    }

    @Filter("/plan/**")
    @Requires(property = "spec.name", value = SPEC)
    static class ToggleableFilter implements HttpServerFilter, Toggleable {
        volatile boolean enabled = true;

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public int getOrder() {
            return 7;
        }

        @Override
        public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Publishers.map(chain.proceed(request), response -> response.header("X-Filter", "G"));
        }
    }

    @ServerFilter("/plan/static")
    @Order(8)
    @Requires(property = "spec.name", value = SPEC)
    static class FilterH {
        @ResponseFilter
        void filter(MutableHttpResponse<?> response) {
            response.header("X-Filter", "H");
        }
    }
}
