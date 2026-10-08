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

import io.micronaut.core.annotation.NonNull;

import java.util.function.Function;

/**
 * Store a reference value in a {@link Memoizer} object.
 *
 * @param <M> The memoized object type
 * @param <R> The stored field type
 * @author Jonas Konrad
 * @since 4.10.0
 */
public final class MemoizedReference<M extends Memoizer<M>, R> {
    final MemoizerNamespace<M> namespace;
    final Function<? super M, ? extends R> compute;
    final int index;

    MemoizedReference(MemoizerNamespace<M> namespace, Function<? super M, ? extends R> compute, int index) {
        this.namespace = namespace;
        this.compute = compute;
        this.index = index;
    }

    /**
     * Get a memoized reference value.
     *
     * @param memoizer The memoizer to load this field from
     * @return The memoized value
     * @implNote The default implementation does no storage and computes the memoized value each time.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public R get(@NonNull M memoizer) {
        if (memoizer instanceof AbstractMemoizer am) {
            return (R) am.getMemoized(this);
        } else {
            return fallback(memoizer);
        }
    }

    @SuppressWarnings({"unchecked"})
    private R fallback(@NonNull M memoizer) {
        if (memoizer instanceof MemoizerDelegate<?> md) {
            return get((M) md.getMemoizerDelegate());
        } else {
            return compute.apply(memoizer);
        }
    }
}
