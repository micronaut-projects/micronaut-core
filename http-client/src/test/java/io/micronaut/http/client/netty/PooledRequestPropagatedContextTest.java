package io.micronaut.http.client.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.AsyncHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.channel.EventLoopGroup;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A request waiting for a pooled connection must complete in its own caller's propagated context, not in the
 * context of the exchange that released the connection.
 */
class PooledRequestPropagatedContextTest {
    private static final String SPEC_NAME = "PooledRequestPropagatedContextTest";

    @ParameterizedTest
    @ValueSource(strings = {"V4_0", "V4_9"})
    void waitingRequestKeepsItsOwnContext(String poolVersion) throws Exception {
        Map<String, Object> configuration = Map.of(
            "spec.name", SPEC_NAME,
            "micronaut.netty.event-loops.default.num-threads", 1,
            "micronaut.http.client.pool.version", poolVersion,
            "micronaut.http.client.pool.max-concurrent-http1-connections", 1
        );
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class, configuration);
             HttpClient httpClient = server.getApplicationContext().createBean(HttpClient.class, server.getURL())) {
            AsyncHttpClient client = httpClient.toAsync();
            // open the single pooled connection
            client.retrieve(HttpRequest.GET("/pooled-ctx"), String.class).toCompletableFuture().get(10, TimeUnit.SECONDS);

            CompletableFuture<String> userA = new CompletableFuture<>();
            CompletableFuture<String> userB = new CompletableFuture<>();
            CompletableFuture<String> anonymous = new CompletableFuture<>();
            server.getApplicationContext().getBean(EventLoopGroup.class).next().execute(() -> {
                PropagatedContext.getOrEmpty().plus(new User("a")).propagate(() -> call(client, userA));
                PropagatedContext.getOrEmpty().plus(new User("b")).propagate(() -> call(client, userB));
                call(client, anonymous);
            });

            assertEquals("a", userA.get(10, TimeUnit.SECONDS));
            assertEquals("b", userB.get(10, TimeUnit.SECONDS));
            assertEquals("none", anonymous.get(10, TimeUnit.SECONDS));
        }
    }

    private static void call(AsyncHttpClient client, CompletableFuture<String> seen) {
        client.retrieve(HttpRequest.GET("/pooled-ctx"), String.class).whenComplete((body, failure) ->
            seen.complete(PropagatedContext.getOrEmpty().find(User.class).map(User::name).orElse("none")));
    }

    private record User(String name) implements PropagatedContextElement {
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class PooledCtxController {
        @Get("/pooled-ctx")
        String get() {
            return "ok";
        }
    }
}
