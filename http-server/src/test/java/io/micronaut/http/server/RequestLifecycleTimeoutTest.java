package io.micronaut.http.server;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.RequestTimeout;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The request timeout of a route, run through the request lifecycle without a server.
 */
class RequestLifecycleTimeoutTest {

    private static final String SPEC = "RequestLifecycleTimeoutTest";
    static volatile CountDownLatch fileClosed = new CountDownLatch(1);
    static volatile CountDownLatch bodyClosed = new CountDownLatch(1);
    static volatile CountDownLatch reactiveCancelled = new CountDownLatch(1);

    private static HttpResponse<?> respond(ApplicationContext context, String path) throws Exception {
        RequestLifecycle lifecycle = new RequestLifecycle(context.getBean(RouteExecutor.class)) {
        };
        // a server binds the propagated context of the request
        return PropagatedContext.empty().plus(new PropagatedContextElement() { }).propagate(() -> lifecycle.normalFlow(HttpRequest.GET(path)))
            .toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private static ApplicationContext context(Map<String, Object> properties) {
        Map<String, Object> all = new HashMap<>(properties);
        all.put("spec.name", SPEC);
        return ApplicationContext.run(all);
    }

    private static void await(CountDownLatch latch, String message) throws InterruptedException {
        assertTrue(latch.await(10, TimeUnit.SECONDS), message);
    }

    @Test
    void aRouteInTimeAnswersAndASlowRouteTimesOut() throws Exception {
        try (ApplicationContext context = context(Map.of())) {
            assertEquals(HttpStatus.OK, respond(context, "/lifecycle/fast").getStatus());
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, respond(context, "/lifecycle/slow").getStatus());
            assertEquals(HttpStatus.OK, respond(context, "/lifecycle/disabled").getStatus());
            // no timeout and no default: the slow route answers
            assertEquals(HttpStatus.OK, respond(context, "/lifecycle/untimed").getStatus());
        }
    }

    @Test
    void theServerDefaultAppliesToARouteWithoutItsOwn() throws Exception {
        try (ApplicationContext context = context(Map.of("micronaut.server.request-timeout", "200ms"))) {
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, respond(context, "/lifecycle/untimed").getStatus());
            assertEquals(HttpStatus.OK, respond(context, "/lifecycle/disabled").getStatus());
        }
    }

    @Test
    void reactiveAndAsynchronousRoutesTimeOut() throws Exception {
        reactiveCancelled = new CountDownLatch(1);
        try (ApplicationContext context = context(Map.of())) {
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, respond(context, "/lifecycle/reactive").getStatus());
            await(reactiveCancelled, "the reactive route was cancelled");
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, respond(context, "/lifecycle/future").getStatus());
            assertEquals(HttpStatus.OK, respond(context, "/lifecycle/reactive-fast").getStatus());
        }
    }

    @Test
    void aLateResponseIsReleased() throws Exception {
        fileClosed = new CountDownLatch(1);
        bodyClosed = new CountDownLatch(1);
        try (ApplicationContext context = context(Map.of())) {
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, respond(context, "/lifecycle/late-file").getStatus());
            await(fileClosed, "the stream of the late file was closed");
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, respond(context, "/lifecycle/late-closeable").getStatus());
            await(bodyClosed, "the late body was closed");
        }
    }

    @Test
    void theConfigurationIgnoresANonPositiveDefault() {
        HttpServerConfiguration configuration = new HttpServerConfiguration();
        configuration.setRequestTimeout(java.time.Duration.ZERO);
        assertEquals(null, configuration.getRequestTimeout());
        configuration.setRequestTimeout(java.time.Duration.ofSeconds(-1));
        assertEquals(null, configuration.getRequestTimeout());
        configuration.setRequestTimeout(null);
        assertEquals(null, configuration.getRequestTimeout());
        configuration.setRequestTimeout(java.time.Duration.ofSeconds(1));
        assertEquals(java.time.Duration.ofSeconds(1), configuration.getRequestTimeout());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * A body that records that it was closed.
     */
    public static final class ClosingBody implements AutoCloseable {
        @Override
        public void close() {
            bodyClosed.countDown();
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Controller("/lifecycle")
    @Produces(MediaType.TEXT_PLAIN)
    static class TimeoutController {

        @Get("/fast")
        @RequestTimeout("2s")
        String fast() {
            return "fast";
        }

        @Get("/slow")
        @RequestTimeout("200ms")
        @ExecuteOn(TaskExecutors.BLOCKING)
        String slow() {
            sleep(600);
            return "slow";
        }

        @Get("/disabled")
        @RequestTimeout("0")
        @ExecuteOn(TaskExecutors.BLOCKING)
        String disabled() {
            sleep(400);
            return "disabled";
        }

        @Get("/untimed")
        @ExecuteOn(TaskExecutors.BLOCKING)
        String untimed() {
            sleep(400);
            return "untimed";
        }

        @Get("/reactive")
        @RequestTimeout("200ms")
        Mono<String> reactive() {
            return Mono.<String>never().doOnCancel(() -> reactiveCancelled.countDown());
        }

        @Get("/reactive-fast")
        @RequestTimeout("2s")
        Mono<String> reactiveFast() {
            return Mono.just("fast");
        }

        @Get("/future")
        @RequestTimeout("200ms")
        CompletableFuture<String> future() {
            return new CompletableFuture<>();
        }

        @Get("/late-file")
        @RequestTimeout("200ms")
        @ExecuteOn(TaskExecutors.BLOCKING)
        StreamedFile lateFile() {
            sleep(500);
            InputStream stream = new ByteArrayInputStream("late".getBytes(StandardCharsets.UTF_8)) {
                @Override
                public void close() throws IOException {
                    fileClosed.countDown();
                    super.close();
                }
            };
            return new StreamedFile(stream, MediaType.TEXT_PLAIN_TYPE);
        }

        @Get("/late-closeable")
        @RequestTimeout("200ms")
        @ExecuteOn(TaskExecutors.BLOCKING)
        HttpResponse<ClosingBody> lateCloseable() {
            sleep(500);
            return HttpResponse.ok(new ClosingBody());
        }
    }
}
