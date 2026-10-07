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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The loop of {@link BodyElements#forEach}: the next element is read when the stage of the
 * consumer for the previous one completed. Whichever of a stage and the loop gets to the end of a
 * step second continues: the loop itself when the stages completed at once, so that elements that
 * are available at once do not deepen the stack, else the thread that completed the stage. The
 * stages are never asked whether they are done, nor converted with
 * {@link CompletionStage#toCompletableFuture()}, which a stage may refuse.
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class BodyElementsLoop<T> {

    private static final Logger LOG = LoggerFactory.getLogger(BodyElementsLoop.class);

    private final Supplier<? extends CompletionStage<Optional<T>>> next;
    private final Function<? super T, ? extends CompletionStage<?>> consumer;
    private final CompletableFuture<@Nullable Void> result;

    private BodyElementsLoop(Supplier<? extends CompletionStage<Optional<T>>> next,
                             Function<? super T, ? extends CompletionStage<?>> consumer,
                             CompletableFuture<@Nullable Void> result) {
        this.next = next;
        this.consumer = consumer;
        this.result = result;
    }

    /**
     * Consume the elements until the end, a failure, or the completion of the result by another
     * party, e.g. closing the elements.
     *
     * @param next     Reads the next element
     * @param consumer Consumes an element
     * @param result   Completes when the elements were consumed
     * @param <T>      The type of an element
     */
    static <T> void run(Supplier<? extends CompletionStage<Optional<T>>> next,
                        Function<? super T, ? extends CompletionStage<?>> consumer,
                        CompletableFuture<@Nullable Void> result) {
        new BodyElementsLoop<>(next, consumer, result).loop();
    }

    private void loop() {
        while (!result.isDone()) {
            AtomicBoolean handOff = new AtomicBoolean();
            step(proceed -> {
                if (proceed && handOff.getAndSet(true)) {
                    loop();
                }
            });
            if (!handOff.getAndSet(true)) {
                return;
            }
        }
    }

    private void step(StepDone done) {
        CompletionStage<Optional<T>> element;
        try {
            element = Objects.requireNonNull(next.get(), "The elements returned no stage");
        } catch (Throwable e) {
            finish(e);
            done.accept(false);
            return;
        }
        element.whenComplete((value, error) -> {
            if (error != null) {
                finish(error);
                done.accept(false);
                return;
            }
            // no optional is the end too
            Optional<T> present = Objects.requireNonNullElse(value, Optional.empty());
            if (present.isEmpty()) {
                finish(null);
                done.accept(false);
                return;
            }
            if (result.isDone()) {
                // the elements were closed meanwhile: nobody takes the element
                discard(present.get());
                done.accept(false);
                return;
            }
            CompletionStage<?> consumed;
            try {
                consumed = Objects.requireNonNull(consumer.apply(present.get()), "The consumer returned no stage");
            } catch (Throwable e) {
                finish(e);
                done.accept(false);
                return;
            }
            consumed.whenComplete((ignored, consumerError) -> {
                if (consumerError != null) {
                    finish(consumerError);
                    done.accept(false);
                } else {
                    done.accept(true);
                }
            });
        });
    }

    /**
     * Release an element that is produced after the elements were closed, if it holds
     * resources, e.g. a {@link ByteBody}.
     *
     * @param element The element
     */
    static void discard(Object element) {
        if (element instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                LOG.debug("Failed to close an element produced after the elements were closed", e);
            }
        }
    }

    private void finish(@Nullable Throwable error) {
        if (error == null) {
            result.complete(null);
        } else {
            result.completeExceptionally(error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
        }
    }

    /**
     * Called once when a step finished.
     */
    @FunctionalInterface
    private interface StepDone {
        /**
         * @param proceed Whether to read the next element
         */
        void accept(boolean proceed);
    }
}
