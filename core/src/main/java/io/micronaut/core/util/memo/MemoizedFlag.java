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

import java.util.function.Predicate;

/**
 * Store a boolean value in a {@link Memoizer} object.
 *
 * @param <M> The memoized object type
 * @author Jonas Konrad
 * @since 4.10.0
 */
@SuppressWarnings("unused")
public abstract sealed class MemoizedFlag<M extends Memoizer<M>> {
    private MemoizedFlag() {
    }

    abstract boolean compute(M memoizer);

    /**
     * Get the memoized boolean value.
     *
     * @param memoizer The memoizer to load or compute this field from
     * @return The memoized value
     * @implNote The default implementation does no storage and computes the memoized value each time.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean get(@NonNull M memoizer) {
        if (memoizer instanceof AbstractMemoizer am) {
            return am.getMemoized(this);
        } else {
            return fallback(memoizer);
        }
    }

    @SuppressWarnings({"unchecked"})
    private boolean fallback(@NonNull M memoizer) {
        if (memoizer instanceof MemoizerDelegate<?> md) {
            return get((M) md.getMemoizerDelegate());
        } else {
            return compute(memoizer);
        }
    }

    /**
     * Implementation that stores in the {@code long} bitmask.
     *
     * @param <M> The memoized object type
     */
    static final class InBitmask<M extends Memoizer<M>> extends MemoizedFlag<M> {
        final MemoizerNamespace<M> namespace;
        final Predicate<? super M> compute;
        final long maskIsSet;
        final long maskValue;

        InBitmask(MemoizerNamespace<M> namespace, Predicate<? super M> compute, int index) {
            this.namespace = namespace;
            this.compute = compute;
            assert index < 32;
            maskIsSet = (1L << (index * 2));
            maskValue = (1L << (index * 2 + 1));
        }

        @Override
        boolean compute(M memoizer) {
            return compute.test(memoizer);
        }
    }

    /**
     * Implementation that stores using {@link MemoizedReference}. This should be avoided, but
     * exists as a fallback when there are too many flags in a namespace.
     *
     * @param <M> The memoized object type
     */
    static final class InReference<M extends Memoizer<M>> extends MemoizedFlag<M> {
        final MemoizedReference<M, Boolean> reference;

        InReference(MemoizedReference<M, Boolean> reference) {
            this.reference = reference;
        }

        @Override
        boolean compute(M memoizer) {
            return reference.compute.apply(memoizer);
        }
    }
}
