/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/**
 * An {@link AsyncRequestBody} that a controller method or a request filter method was invoked
 * with, as the server sees it: when the method completed, the server releases what its reads of
 * the body left open, e.g. the elements it did not read or a file it was still writing, before
 * the response is written or the filter chain continues, or, for a streamed response of a
 * controller method, when its stream ended.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public interface ReleasableRequestBody {

    /**
     * Release what the reads of the body left open: abort the reads that are still running, e.g.
     * {@link AsyncRequestBody#elements(Class)}, {@link AsyncRequestBody#parts()} or
     * {@link AsyncRequestBody#transferTo(java.nio.file.Path)}, and release the content they
     * buffered or staged, the reads of its copies too. Releasing again does nothing more.
     *
     * @return Completes when released, exceptionally when releasing failed
     */
    CompletionStage<Void> releaseBody();

    /**
     * Release the body once the flow of the method that was invoked with it completed, before the
     * result of the flow is delivered: a failure to release fails a successful result, and is
     * added as suppressed to a failure of the flow. A reactive flow stays reactive, and releases
     * when it is subscribed to and completes.
     *
     * @param flow The flow of the method
     * @param body The body the method was invoked with
     * @param <T>  The result type
     * @return The flow, which completes once the body was released
     */
    static <T> ExecutionFlow<T> releaseAfter(ExecutionFlow<T> flow, ReleasableRequestBody body) {
        if (flow instanceof ReactiveExecutionFlow<T> reactive) {
            Mono<T> released = Mono.from(reactive.toPublisher())
                .materialize()
                .flatMap(signal -> Mono.<T>create(sink -> release(body).whenComplete((ignored, releaseError) -> {
                    Throwable error = signal.getThrowable();
                    Throwable failure = failure(error, releaseError);
                    if (failure != null) {
                        sink.error(failure);
                    } else if (signal.hasValue()) {
                        sink.success(signal.get());
                    } else {
                        sink.success();
                    }
                })));
            return ReactiveExecutionFlow.fromPublisher(released);
        }
        DelayedExecutionFlow<T> result = DelayedExecutionFlow.create();
        result.onCancel(flow::cancel);
        flow.onComplete((value, error) -> release(body).whenComplete((ignored, releaseError) -> {
            Throwable failure = failure(error, releaseError);
            if (failure != null) {
                result.completeExceptionally(failure);
            } else {
                result.complete(value);
            }
        }));
        return result;
    }

    /**
     * Release both bodies.
     *
     * @param first  The first body
     * @param second The second body
     * @return A body that releases both, and fails if releasing either failed
     */
    static ReleasableRequestBody both(ReleasableRequestBody first, ReleasableRequestBody second) {
        return () -> {
            CompletionStage<Void> firstReleased = release(first);
            CompletionStage<Void> secondReleased = release(second);
            CompletableFuture<Void> result = new CompletableFuture<>();
            firstReleased.whenComplete((ignored, firstError) -> secondReleased.whenComplete((ignored2, secondError) -> {
                Throwable failure = failure(firstError == null ? null : unwrap(firstError), secondError);
                if (failure != null) {
                    result.completeExceptionally(failure);
                } else {
                    result.complete(null);
                }
            }));
            return result;
        };
    }

    /**
     * Release the body: a release that throws fails the stage.
     *
     * @param body The body
     * @return Completes when released
     */
    private static CompletionStage<Void> release(ReleasableRequestBody body) {
        try {
            return body.releaseBody();
        } catch (Throwable e) {
            return CompletableFuture.failedStage(e);
        }
    }

    /**
     * The failure of a method once its body was released.
     *
     * @param error        The failure of the method, or {@code null}
     * @param releaseError The failure to release the body, or {@code null}
     * @return The failure of the method, with the failure to release as suppressed, the failure to
     * release, or {@code null}
     */
    private static @Nullable Throwable failure(@Nullable Throwable error, @Nullable Throwable releaseError) {
        if (releaseError == null) {
            return error;
        }
        Throwable released = unwrap(releaseError);
        if (error == null) {
            return released;
        }
        // a Throwable is equal to itself only: a failure cannot suppress itself
        if (!released.equals(error)) {
            error.addSuppressed(released);
        }
        return error;
    }

    private static Throwable unwrap(Throwable error) {
        if ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }
}
