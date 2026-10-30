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
package io.micronaut.http.body.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.body.BodyElements;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * {@link BodyElements} that run a callback once when they are closed, by {@link #close()} or by
 * {@link #closeAsync()}, e.g. to release the request body the elements were read from. Every
 * method goes to the elements themselves, so that they keep their own rules and operations.
 * <b>Internal API.</b>
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class ReleasingBodyElements<T> implements BodyElements<T> {

    private final BodyElements<T> elements;
    private final Runnable release;
    private final AtomicBoolean released = new AtomicBoolean();

    private ReleasingBodyElements(BodyElements<T> elements, Runnable release) {
        this.elements = elements;
        this.release = release;
    }

    /**
     * @param elements The elements
     * @param release  Runs once after the elements were closed
     * @param <T>      The type of an element
     * @return The elements, which run the callback when they are closed
     */
    public static <T> BodyElements<T> onClose(BodyElements<T> elements, Runnable release) {
        return new ReleasingBodyElements<>(Objects.requireNonNull(elements, "elements"), Objects.requireNonNull(release, "release"));
    }

    @Override
    public CompletionStage<Optional<T>> next() {
        return elements.next();
    }

    @Override
    public @Nullable T poll() {
        return elements.poll();
    }

    @Override
    public State state() {
        return elements.state();
    }

    @Override
    public @Nullable Throwable failure() {
        return elements.failure();
    }

    @Override
    public CompletionStage<Void> forEach(Function<? super T, ? extends CompletionStage<?>> consumer) {
        return elements.forEach(consumer);
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        CompletionStage<Void> closed;
        try {
            closed = elements.closeAsync();
        } catch (RuntimeException | Error e) {
            release();
            throw e;
        }
        closed.whenComplete((ignored, error) -> release());
        return closed;
    }

    @Override
    public void close() {
        try {
            elements.close();
        } finally {
            release();
        }
    }

    private void release() {
        if (released.compareAndSet(false, true)) {
            release.run();
        }
    }

    @Override
    public String toString() {
        return elements.toString();
    }
}
