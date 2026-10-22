package io.micronaut.http.client.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.annotation.ClientTimeouts;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * {@link ClientTimeouts} on a declarative client replace the timeouts of the client
 * configuration for its exchanges.
 */
class ClientTimeoutsTest {
    private static ApplicationContext ctx;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of(
            "spec.name", "ClientTimeoutsTest",
            "micronaut.http.client.read-timeout", "1s",
            "client-timeouts-test.read-timeout", "5s"
        ));
        server = ctx.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        server.close();
        ctx.close();
    }

    @Test
    void theTimeoutsOfTheClient() {
        // longer than the read timeout of the configuration
        Assertions.assertEquals("slow", ctx.getBean(TimeoutsClient.class).slow(1500));
    }

    @Test
    void theTimeoutsOfAMethodOverrideTheOnesOfTheClient() {
        TimeoutsClient client = ctx.getBean(TimeoutsClient.class);
        long start = System.nanoTime();
        Assertions.assertThrows(ReadTimeoutException.class, () -> client.shorterRead(3000));
        Duration timedOutAfter = Duration.ofNanos(System.nanoTime() - start);
        Assertions.assertTrue(timedOutAfter.compareTo(Duration.ofMillis(900)) < 0, "Timed out after " + timedOutAfter);
    }

    @Test
    void theRequestTimeoutOfAMethod() {
        TimeoutsClient client = ctx.getBean(TimeoutsClient.class);
        Assertions.assertThrows(ReadTimeoutException.class, () -> Mono.from(client.shorterRequest(3000)).block());
    }

    @Test
    void aMethodKeepsTheTimeoutsOfTheClientItDoesNotSet() {
        // the read timeout of the client, longer than the read timeout of the configuration
        Assertions.assertEquals("slow", ctx.getBean(TimeoutsClient.class).longerRequest(1500));
    }

    @Test
    void anInvalidTimeoutFailsTheCall() {
        TimeoutsClient client = ctx.getBean(TimeoutsClient.class);
        IllegalArgumentException invalid = Assertions.assertThrows(IllegalArgumentException.class, () -> client.invalid(0));
        Assertions.assertTrue(invalid.getMessage().contains("soon"), invalid.getMessage());
        Assertions.assertThrows(IllegalArgumentException.class, () -> client.zero(0));
    }

    @Client("/client-timeouts")
    @ClientTimeouts(read = "${client-timeouts-test.read-timeout}")
    @Requires(property = "spec.name", value = "ClientTimeoutsTest")
    interface TimeoutsClient {
        @Get(value = "/slow", consumes = MediaType.TEXT_PLAIN)
        String slow(@QueryValue long delay);

        @Get(value = "/slow", consumes = MediaType.TEXT_PLAIN)
        @ClientTimeouts(read = "200ms")
        String shorterRead(@QueryValue long delay);

        @Get(value = "/slow", consumes = MediaType.TEXT_PLAIN)
        @ClientTimeouts(request = "300ms")
        Mono<String> shorterRequest(@QueryValue long delay);

        @Get(value = "/slow", consumes = MediaType.TEXT_PLAIN)
        @ClientTimeouts(request = "10s")
        String longerRequest(@QueryValue long delay);

        @Get(value = "/slow", consumes = MediaType.TEXT_PLAIN)
        @ClientTimeouts(read = "soon")
        String invalid(@QueryValue long delay);

        @Get(value = "/slow", consumes = MediaType.TEXT_PLAIN)
        @ClientTimeouts(read = "0s")
        String zero(@QueryValue long delay);
    }

    @Controller("/client-timeouts")
    @Requires(property = "spec.name", value = "ClientTimeoutsTest")
    static class SlowController {
        @Get(value = "/slow", produces = MediaType.TEXT_PLAIN)
        Mono<String> slow(@QueryValue long delay, HttpRequest<?> request) {
            return Mono.delay(Duration.ofMillis(delay)).thenReturn("slow");
        }
    }
}
