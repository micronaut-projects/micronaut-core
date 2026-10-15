package io.micronaut.http.body;

import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReleasableRequestBody#releaseAfter}: the body is released when the flow of the method
 * ends, once, whether the flow completed, was cancelled, or both.
 */
class ReleasableRequestBodyTest {

    @Test
    void aCancelledReactiveFlowReleasesTheBody() {
        Body body = new Body();
        AtomicBoolean upstreamCancelled = new AtomicBoolean();
        ExecutionFlow<String> flow = ReactiveExecutionFlow.fromPublisher(Mono.<String>never().doOnCancel(() -> upstreamCancelled.set(true)));

        Disposable subscription = Mono.from(ReactiveExecutionFlow.toPublisher(ReleasableRequestBody.releaseAfter(flow, body))).subscribe();
        assertEquals(0, body.releases.get(), "A pending flow keeps the body");

        subscription.dispose();
        assertTrue(upstreamCancelled.get());
        assertEquals(1, body.releases.get());
    }

    @Test
    void aReactiveFlowCancelledThroughTheFlowReleasesTheBody() {
        Body body = new Body();
        ExecutionFlow<String> flow = ReactiveExecutionFlow.fromPublisher(Mono.never());

        ExecutionFlow<String> released = ReleasableRequestBody.releaseAfter(flow, body);
        released.onComplete((value, error) -> {
        });
        assertEquals(0, body.releases.get(), "A pending flow keeps the body");

        released.cancel();
        assertEquals(1, body.releases.get());
    }

    @Test
    void aReactiveFlowCancelledWhileTheBodyIsReleasedDoesNotReleaseAgain() {
        Body body = new Body();
        body.released = new CompletableFuture<>();
        Sinks.One<String> result = Sinks.one();
        ExecutionFlow<String> flow = ReactiveExecutionFlow.fromPublisher(result.asMono());

        Disposable subscription = Mono.from(ReactiveExecutionFlow.toPublisher(ReleasableRequestBody.releaseAfter(flow, body))).subscribe();
        result.tryEmitValue("done");
        assertEquals(1, body.releases.get(), "The flow completed: the body is being released");

        subscription.dispose();
        assertEquals(1, body.releases.get());
    }

    @Test
    void aReactiveFlowCancelledAfterItCompletedDoesNotReleaseAgain() {
        Body body = new Body();
        Sinks.One<String> result = Sinks.one();
        ExecutionFlow<String> flow = ReactiveExecutionFlow.fromPublisher(result.asMono());
        AtomicReference<String> value = new AtomicReference<>();

        ExecutionFlow<String> released = ReleasableRequestBody.releaseAfter(flow, body);
        released.onComplete((v, error) -> value.set(v));
        result.tryEmitValue("done");
        assertEquals("done", value.get());
        assertEquals(1, body.releases.get());

        released.cancel();
        assertEquals(1, body.releases.get());
    }

    @Test
    void aCancelledFlowReleasesTheBody() {
        Body body = new Body();
        DelayedExecutionFlow<String> flow = DelayedExecutionFlow.create();

        ExecutionFlow<String> released = ReleasableRequestBody.releaseAfter(flow, body);
        released.onComplete((value, error) -> {
        });
        assertEquals(0, body.releases.get(), "A pending flow keeps the body");

        released.cancel();
        assertTrue(flow.isCancelled());
        assertEquals(1, body.releases.get());
    }

    @Test
    void aCancelledFlowThatCompletesAfterwardsDoesNotReleaseAgain() {
        Body body = new Body();
        CompletableFuture<String> stage = new CompletableFuture<>();
        ExecutionFlow<String> flow = CompletableFutureExecutionFlow.just(stage);

        ExecutionFlow<String> released = ReleasableRequestBody.releaseAfter(flow, body);
        released.cancel();
        assertEquals(1, body.releases.get());

        // cancelling is a hint: the stage of the method completes anyway
        stage.complete("done");
        assertEquals(1, body.releases.get());
    }

    @Test
    void aFlowCancelledAfterItCompletedDoesNotReleaseAgain() {
        Body body = new Body();
        DelayedExecutionFlow<String> flow = DelayedExecutionFlow.create();
        AtomicReference<String> value = new AtomicReference<>();

        ExecutionFlow<String> released = ReleasableRequestBody.releaseAfter(flow, body);
        released.onComplete((v, error) -> value.set(v));
        flow.complete("done");
        assertEquals("done", value.get());
        assertEquals(1, body.releases.get());

        released.cancel();
        assertEquals(1, body.releases.get());
    }

    @Test
    void aFailureToReleaseACancelledFlowIsNotThrownAtTheOneThatCancels() {
        Body reactiveBody = new Body();
        reactiveBody.released = CompletableFuture.failedFuture(new IllegalStateException("Cannot release"));
        Disposable subscription = Mono.from(ReactiveExecutionFlow.toPublisher(
            ReleasableRequestBody.releaseAfter(ReactiveExecutionFlow.fromPublisher(Mono.<String>never()), reactiveBody))).subscribe();
        subscription.dispose();
        assertEquals(1, reactiveBody.releases.get());

        Body throwingBody = new Body();
        throwingBody.thrown = new IllegalStateException("Cannot release");
        ExecutionFlow<String> released = ReleasableRequestBody.releaseAfter(DelayedExecutionFlow.create(), throwingBody);
        released.cancel();
        assertEquals(1, throwingBody.releases.get());
    }

    @Test
    void aCompletedFlowStillFailsWithTheFailureToRelease() {
        Body body = new Body();
        IllegalStateException failure = new IllegalStateException("Cannot release");
        body.released = CompletableFuture.failedFuture(failure);
        DelayedExecutionFlow<String> flow = DelayedExecutionFlow.create();
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<String> value = new AtomicReference<>();

        ReleasableRequestBody.releaseAfter(flow, body).onComplete((v, e) -> {
            value.set(v);
            error.set(e);
        });
        assertFalse(body.releases.get() > 0);
        flow.complete("done");
        assertNull(value.get());
        assertSame(failure, error.get());
        assertEquals(1, body.releases.get());
    }

    private static final class Body implements ReleasableRequestBody {
        final AtomicInteger releases = new AtomicInteger();
        CompletionStage<Void> released = CompletableFuture.completedFuture(null);
        RuntimeException thrown;

        @Override
        public CompletionStage<Void> releaseBody() {
            releases.incrementAndGet();
            if (thrown != null) {
                throw thrown;
            }
            return released;
        }
    }
}
