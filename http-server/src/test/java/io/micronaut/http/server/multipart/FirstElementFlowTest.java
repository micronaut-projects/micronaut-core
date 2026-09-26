package io.micronaut.http.server.multipart;

import io.micronaut.core.execution.ExecutionFlow;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstElementFlowTest {

    @Test
    void firstElementCancelsRest() {
        AtomicLong requested = new AtomicLong();
        AtomicBoolean cancelled = new AtomicBoolean();
        Flux<String> source = Flux.just("a", "b", "c")
            .doOnRequest(requested::addAndGet)
            .doOnCancel(() -> cancelled.set(true));

        FirstElementFlow.Settled<String> settled = FirstElementFlow.settle(FirstElementFlow.first(source));

        assertTrue(settled.isDone());
        assertEquals(Optional.of("a"), settled.valueNow());
        assertEquals("a", settled.flow().tryCompleteValue());
        assertEquals(1, requested.get());
        assertTrue(cancelled.get());
    }

    @Test
    void emptyCompletesWithoutValue() {
        FirstElementFlow.Settled<String> settled = FirstElementFlow.settle(FirstElementFlow.first(Mono.empty()));

        assertTrue(settled.isDone());
        assertEquals(Optional.empty(), settled.valueNow());
        assertNull(settled.flow().tryCompleteValue());
        assertNull(settled.flow().tryCompleteError());
    }

    @Test
    void errorPropagates() {
        IllegalStateException failure = new IllegalStateException("boom");
        FirstElementFlow.Settled<String> settled = FirstElementFlow.settle(FirstElementFlow.first(Mono.error(failure)));

        assertTrue(settled.isDone());
        CompletionException thrown = assertThrows(CompletionException.class, settled::valueNow);
        assertSame(failure, thrown.getCause());
        assertSame(failure, settled.flow().tryCompleteError());
    }

    @Test
    void pendingUntilFirstElement() {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        ExecutionFlow<String> flow = FirstElementFlow.first(sink.asFlux());
        FirstElementFlow.Settled<String> settled = FirstElementFlow.settle(flow.map(s -> s + "!"));

        assertFalse(settled.isDone());
        assertEquals(Optional.empty(), settled.valueNow());
        sink.tryEmitNext("x");
        assertTrue(settled.isDone());
        assertEquals(Optional.of("x!"), settled.valueNow());
    }

    @Test
    void cancellingWaitFlowCancelsUpstream() {
        AtomicBoolean cancelled = new AtomicBoolean();
        Flux<String> source = Flux.<String>never().doOnCancel(() -> cancelled.set(true));
        FirstElementFlow.Settled<String> settled = FirstElementFlow.settle(FirstElementFlow.first(source));

        settled.flow().cancel();

        assertTrue(cancelled.get());
        assertFalse(settled.isDone());
    }
}
