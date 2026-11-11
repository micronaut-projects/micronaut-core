package io.micronaut.http.server.netty.cors;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.cors.CorsFilter;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsLocatedRoutesTest {
    private static final String SPEC_NAME = "CorsLocatedRoutesTest";

    @Test
    void corsFilterDoesNotRetainRoutesOfLocatedRouteTables() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME, "micronaut.server.port", -1));
             HttpClient client = HttpClient.newHttpClient()) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            CorsFilter filter = ctx.getBean(CorsFilter.class);
            Field field = CorsFilter.class.getDeclaredField("routeConfigurations");
            field.setAccessible(true);
            Map<?, ?> cache = (Map<?, ?>) field.get(filter);
            for (int i = 0; i < 50; i++) {
                client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + "/cors-located/shops/s" + i + "/items/1"))
                    .header("Origin", "https://foo.example").GET().build(), BodyHandlers.discarding());
            }
            assertTrue(cache.size() < 5, "CORS route configurations retained " + cache.size() + " routes");
        }
    }

    record Shop(String name) {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.locate("/cors-located/shops/{shop}", (request, pathVariables) -> new Shop(pathVariables.getString("shop")), shop -> new ShopRoutes());
        }
    }

    static final class ShopRoutes implements LocatedRoutes<Shop> {
        @Override
        public Argument<Shop> targetType() {
            return Argument.of(Shop.class);
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<Shop> located) {
            located.GET("/items/{index}").handle((request, pathVariables, shop) -> HttpResponse.ok("x"));
        }
    }
}
