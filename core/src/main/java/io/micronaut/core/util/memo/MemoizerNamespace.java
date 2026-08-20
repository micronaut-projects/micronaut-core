/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.core.util.memo;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A namespace for a {@link Memoizer}. Each memoizer <i>type</i> is associated with exactly one
 * namespace. For example, {@link io.micronaut.core.annotation.AnnotationMetadata} has the
 * namespace {@link io.micronaut.core.annotation.AnnotationMetadata#MEMOIZER_NAMESPACE}.
 *
 * <p>The namespace can be used to create new "fields" that are then memoized in the
 * {@link Memoizer} instances. Note that fields should be used sparingly, as each field may use
 * some memory in <i>all</i> {@link Memoizer} instances in the namespace, not just those where the
 * field is actually used.
 *
 * @param <M> The memoizer type
 * @author Jonas Konrad
 * @since 4.10.0
 */
public final class MemoizerNamespace<M extends Memoizer<M>> {
    private final AtomicInteger nextReference = new AtomicInteger();
    private final AtomicInteger nextFlag = new AtomicInteger();

    private MemoizerNamespace() {
    }

    /**
     * Create a new namespace. There should be exactly one namespace per {@link Memoizer} type.
     *
     * <p><b>Note: This is internal to micronaut-core for the moment.</b></p>
     *
     * @param <M> The memoizer type
     * @return The namespace
     */
    @Internal
    @NonNull
    public static <M extends Memoizer<M>> MemoizerNamespace<M> create() {
        return new MemoizerNamespace<>();
    }

    /**
     * Create a new memoizer field that can store an arbitrary reference type.
     *
     * @param compute The function to compute the reference value
     * @param <R>     The field type
     * @return The field
     */
    @NonNull
    public <R> MemoizedReference<M, R> newReference(@NonNull Function<? super M, ? extends R> compute) {
        return new MemoizedReference<>(this, compute, nextReference.getAndIncrement());
    }

    /**
     * Create a new memoizer field that can store a boolean flag.
     *
     * @param compute The function to compute the boolean value
     * @return The field
     */
    @NonNull
    public MemoizedFlag<M> newFlag(@NonNull Predicate<? super M> compute) {
        int index = nextFlag.getAndIncrement();
        if (index >= 32) {
            return new MemoizedFlag.InReference<>(newReference(compute::test));
        } else {
            return new MemoizedFlag.InBitmask<>(this, compute, index);
        }
    }
}
