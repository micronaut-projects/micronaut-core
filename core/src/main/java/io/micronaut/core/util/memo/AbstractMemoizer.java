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
import io.micronaut.core.util.ArrayUtils;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Implementation of {@link Memoizer} with backing storage.
 *
 * @param <M> This memoizer type
 * @author Jonas Konrad
 * @since 4.10.0
 */
@Internal
public abstract class AbstractMemoizer<M extends Memoizer<M>> implements Memoizer<M> {
    private static final Object NULL_SENTINEL = new Object();
    private static final VarHandle ITEMS_ENTRY = MethodHandles.arrayElementVarHandle(Object[].class).withInvokeExactBehavior();

    private Object[] items = ArrayUtils.EMPTY_OBJECT_ARRAY;
    private long flags;

    /**
     * Get the namespace that should be used for this memoizer. This is a method instead of a field
     * to save on memory. This namespace is only spot-checked on field population.
     *
     * @return The namespace
     */
    @NonNull
    @Internal
    protected abstract MemoizerNamespace<M> getMemoizerNamespace();

    /**
     * Clear all memoized values, for use when this instance is modified and memoization may now
     * return a different value. This is a cheap operation.
     */
    @Internal
    protected final void clearMemoized() {
        items = ArrayUtils.EMPTY_OBJECT_ARRAY;
        flags = 0;
    }

    @SuppressWarnings("unchecked")
    final <R> R getMemoized(MemoizedReference<M, R> reference) {
        Object[] items = this.items;
        int index = reference.index;
        if (index >= items.length) {
            items = ensureCapacity(items, index);
        }
        Object item = ITEMS_ENTRY.getAcquire(items, index);
        if (item == null) {
            item = computeItem(reference, items, index);
        }
        if (item == NULL_SENTINEL) {
            item = null;
        }
        return (R) item;
    }

    private Object[] ensureCapacity(Object[] items, int accessedIndex) {
        Object[] newItems = new Object[accessedIndex + 1];
        for (int i = 0; i < items.length; i++) {
            ITEMS_ENTRY.setRelease(newItems, i, ITEMS_ENTRY.getAcquire(items, i));
        }
        this.items = newItems;
        return newItems;
    }

    @SuppressWarnings("unchecked")
    private Object computeItem(MemoizedReference<M, ?> reference, Object[] items, int index) {
        if (reference.namespace != getMemoizerNamespace()) {
            throw new IllegalStateException("Reference not in right namespace");
        }
        Object item = reference.compute.apply((M) this);
        if (item == null) {
            item = NULL_SENTINEL;
        }
        ITEMS_ENTRY.setRelease(items, index, item);
        return item;
    }

    final boolean getMemoized(@NonNull MemoizedFlag<M> flag) {
        if (!(flag instanceof MemoizedFlag.InBitmask<M> ib)) {
            return getMemoizedViaReference(flag);
        }
        long flags = this.flags;
        if ((flags & ib.maskIsSet) == 0) {
            flags = computeFlag(flags, ib);
        }
        return (flags & ib.maskValue) != 0;
    }

    private boolean getMemoizedViaReference(MemoizedFlag<M> flag) {
        return getMemoized(((MemoizedFlag.InReference<M>) flag).reference);
    }

    @SuppressWarnings("unchecked")
    private long computeFlag(long flags, MemoizedFlag.InBitmask<M> ib) {
        flags |= ib.maskIsSet;
        if (ib.compute.test((M) this)) {
            flags |= ib.maskValue;
        } else {
            flags &= ~ib.maskValue;
        }
        this.flags = flags;
        return flags;
    }
}
