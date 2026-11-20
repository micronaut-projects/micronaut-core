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
package io.micronaut.http.client.limit;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Hands a permit of the concurrency limit over to the selection of an instance by the load
 * balancer, or gives it back when the exchange is cancelled first.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class PermitHandOff {

    private PermitHandOff() {
    }

    /**
     * Whichever comes first wins: the selection, which then ends the permit with the exchange,
     * or the cancellation, which gives the permit back. A selection that arrives later, from a
     * future the cancellation could not stop, is released, not forwarded. A cancellation once the
     * instance was selected, e.g. of a {@code Mono} that cancels its source once it emitted, is
     * for the exchange to handle.
     *
     * @param selected The flow of the selection, which owns the permit once it completes
     * @param permit   The permit
     * @param release  Releases a selection that arrived after the cancellation
     * @param <T>      The type of the selection
     * @return The flow of the selection
     */
    public static <T> ExecutionFlow<T> handOff(ExecutionFlow<T> selected, Runnable permit, Consumer<T> release) {
        if (selected.tryComplete() != null) {
            // a synchronous balancer: no cancellation can come in between
            return selected.onErrorResume(e -> {
                permit.run();
                return ExecutionFlow.error(e);
            });
        }
        DelayedExecutionFlow<T> target = DelayedExecutionFlow.create();
        AtomicBoolean decided = new AtomicBoolean();
        selected.onComplete((@Nullable T resolved, @Nullable Throwable failure) -> {
            if (!decided.compareAndSet(false, true)) {
                if (resolved != null) {
                    release.accept(resolved);
                }
                permit.run();
            } else if (failure != null) {
                permit.run();
                target.completeExceptionally(failure);
            } else {
                target.complete(resolved);
            }
        });
        target.onCancel(() -> {
            if (decided.compareAndSet(false, true)) {
                selected.cancel();
                permit.run();
            }
        });
        return target;
    }
}
