package io.micronaut.dev.http;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevGateFilterHoldTest {

    @Test
    void aRequestProceedsAtOnceWhenNoBatchRuns() throws Exception {
        assertNull(DevGateFilter.hold(CompletableFuture.completedFuture(null), Duration.ofSeconds(30), () -> null).get(1, TimeUnit.SECONDS));
    }

    @Test
    void aHeldRequestProceedsOnceAdmitted() throws Exception {
        CompletableFuture<Void> admitted = new CompletableFuture<>();
        CompletableFuture<HttpResponse<?>> response = DevGateFilter.hold(admitted, Duration.ofSeconds(30), () -> null);
        Thread.sleep(100);
        assertFalse(response.isDone());
        admitted.complete(null);
        assertNull(response.get(1, TimeUnit.SECONDS));
    }

    @Test
    void aRequestHeldLongerThanTheHoldIsAnswered503WithRetryAfter() throws Exception {
        CompletableFuture<Void> admitted = new CompletableFuture<>();
        HttpResponse<?> response = DevGateFilter.hold(admitted, Duration.ofMillis(100), () -> null).get(5, TimeUnit.SECONDS);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatus());
        assertEquals(DevGateFilter.RETRY_AFTER_SECONDS, response.getHeaders().get(HttpHeaders.RETRY_AFTER));
        assertTrue(response.getBody(String.class).orElseThrow().contains("reloading"));
        // admitted later: the answer stays the 503
        admitted.complete(null);
    }

    @Test
    void aFailureAnsweringAnAdmittedRequestFailsItAtOnce() {
        CompletableFuture<Void> admitted = new CompletableFuture<>();
        CompletableFuture<HttpResponse<?>> response = DevGateFilter.hold(admitted, Duration.ofSeconds(30), () -> {
            throw new IllegalStateException("cannot render");
        });
        admitted.complete(null);
        assertTrue(response.isCompletedExceptionally());
    }
}
