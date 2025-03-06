/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.kotlin.processing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.List;

/**
 * Models pageable data. The {@link #from(int, int)} method can be used to construct a new instance to pass to Micronaut Data methods.
 *
 * @author boros
 * @author graemerocher
 * @since 1.0.0
 */
@Introspected
public interface Pageable extends Sort {

    /**
     * Constant for no pagination.
     */
    Pageable UNPAGED = new Pageable() {
        @Override
        public int getNumber() {
            return 0;
        }

        @Override
        public int getSize() {
            return -1;
        }
    };

    /**
     * @return The page number.
     */
    int getNumber();

    /**
     * Maximum size of the page to be returned. A value of -1 indicates no maximum.
     * @return size of the requested page of items
     */
    int getSize();

    /**
     * Offset in the requested collection. Defaults to zero.
     * @return offset in the requested collection
     */
    @JsonIgnore
    default long getOffset() {
        int size = getSize();
        if (size < 0) {
            return 0; // unpaged
        }
        return (long) getNumber() * (long) size;
    }

    /**
     * @return The sort definition to use.
     */
    @NonNull
    default Sort getSort() {
        return Sort.unsorted();
    }

    /**
     * @return The next pageable.
     */
    default @NonNull Pageable next() {
        int size = getSize();
        if (size < 0) {
            // unpaged
            return Pageable.from(0, size, getSort());
        }
        int newNumber = getNumber() + 1;
        // handle overflow
        if (newNumber < 0) {
            return Pageable.from(0, size, getSort());
        } else {
            return Pageable.from(newNumber, size, getSort());
        }
    }

    /**
     * @return The previous pageable
     */
    default @NonNull Pageable previous() {
        int size = getSize();
        if (size < 0) {
            // unpaged
            return Pageable.from(0, size, getSort());
        }
        int newNumber = getNumber() - 1;
        // handle overflow
        if (newNumber < 0) {
            return Pageable.from(0, size, getSort());
        } else {
            return Pageable.from(newNumber, size, getSort());
        }
    }

    /**
     * @return Is unpaged
     */
    @JsonIgnore
    default boolean isUnpaged() {
        return getSize() == -1;
    }

    @NonNull
    @Override
    default Pageable order(@NonNull String propertyName) {
        Sort newSort = getSort().order(propertyName);
        return Pageable.from(getNumber(), getSize(), newSort);
    }

    @Override
    @JsonIgnore
    default boolean isSorted() {
        return getSort().isSorted();
    }

    @NonNull
    @Override
    default Pageable order(@NonNull Order order) {
        Sort newSort = getSort().order(order);
        return Pageable.from(getNumber(), getSize(), newSort);
    }

    @NonNull
    @Override
    default Pageable order(@NonNull String propertyName, @NonNull Order.Direction direction) {
        Sort newSort = getSort().order(propertyName, direction);
        return Pageable.from(getNumber(), getSize(), newSort);
    }

    @NonNull
    @Override
    @JsonIgnore
    default List<Order> getOrderBy() {
        return getSort().getOrderBy();
    }

    /**
     * Creates a new {@link Pageable} at the given offset with a default size of 10.
     * @param page The page
     * @return The pageable
     */
    static @NonNull Pageable from(int page) {
        return new DefaultPageable(page, 10, null);
    }

    /**
     * Creates a new {@link Pageable} at the given offset.
     * @param page The page
     * @param size the size
     * @return The pageable
     */
    static @NonNull Pageable from(int page, int size) {
        return new DefaultPageable(page, size, null);
    }

    /**
     * Creates a new {@link Pageable} at the given offset.
     * @param page The page
     * @param size the size
     * @param sort the sort
     * @return The pageable
     */
    @JsonCreator
    static @NonNull Pageable from(
            @JsonProperty("number") int page,
            @JsonProperty("size") int size,
            @JsonProperty("sort") @Nullable Sort sort) {
        return new DefaultPageable(page, size, sort);
    }

    /**
     * Creates a new {@link Pageable} at the given offset.
     * @param sort the sort
     * @return The pageable
     */
    static @NonNull Pageable from(Sort sort) {
        if (sort == null) {
            return UNPAGED;
        } else {
            return new Pageable() {
                @Override
                public int getNumber() {
                    return 0;
                }

                @Override
                public int getSize() {
                    return -1;
                }

                @NonNull
                @Override
                public Sort getSort() {
                    return sort;
                }
            };
        }
    }

    /**
     * @return A new instance without paging data.
     */
    static @NonNull Pageable unpaged() {
        return UNPAGED;
    }
}
