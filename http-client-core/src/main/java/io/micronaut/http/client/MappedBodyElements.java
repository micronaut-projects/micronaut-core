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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.body.BodyElements;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Elements mapped one by one as they are read.
 *
 * @param <S> The type of an element of the source
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class MappedBodyElements<S, T> implements BodyElements<T> {

    private final BodyElements<S> source;
    private final Function<? super S, ? extends T> mapper;
    private final AtomicReference<@Nullable Throwable> mappingFailure = new AtomicReference<>();
    private volatile boolean closed;

    private MappedBodyElements(BodyElements<S> source, Function<? super S, ? extends T> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    /**
     * @param source The elements
     * @param mapper Maps an element
     * @param <S>    The type of an element of the source
     * @param <T>    The type of an element
     * @return The mapped elements
     */
    public static <S, T> BodyElements<T> map(BodyElements<S> source, Function<? super S, ? extends T> mapper) {
        return new MappedBodyElements<>(Objects.requireNonNull(source, "source"), Objects.requireNonNull(mapper, "mapper"));
    }

    @Override
    public CompletionStage<Optional<T>> next() {
        checkClosed();
        Throwable error = mappingFailure.get();
        return error == null ? source.next().thenApply(element -> element.map(this::mapElement)) : CompletableFuture.failedStage(error);
    }

    @Override
    public @Nullable T poll() {
        checkClosed();
        if (mappingFailure.get() != null) {
            return null;
        }
        S element = source.poll();
        return element == null ? null : mapElement(element);
    }

    @Override
    public State state() {
        return mappingFailure.get() == null ? source.state() : State.FAILED;
    }

    @Override
    public @Nullable Throwable failure() {
        Throwable error = mappingFailure.get();
        return error == null ? source.failure() : error;
    }

    @Override
    public CompletionStage<Void> forEach(Function<? super T, ? extends CompletionStage<?>> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        checkClosed();
        Throwable error = mappingFailure.get();
        if (error != null) {
            try {
                close();
            } catch (Exception | Error closing) {
                if (closing != error) {
                    error.addSuppressed(closing);
                }
            }
            return CompletableFuture.failedStage(error);
        }
        return source.forEach(element -> consumer.apply(mapElement(element))).whenComplete((ignored, failure) -> {
            if (failure != null) {
                closed = true;
            }
        });
    }

    private T mapElement(S element) {
        try {
            return mapper.apply(element);
        } catch (Exception | Error e) {
            mappingFailure.compareAndSet(null, e);
            throw e;
        }
    }

    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException("The elements of the body were closed");
        }
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        closed = true;
        return source.closeAsync();
    }

    @Override
    public void close() {
        closed = true;
        source.close();
    }
}
