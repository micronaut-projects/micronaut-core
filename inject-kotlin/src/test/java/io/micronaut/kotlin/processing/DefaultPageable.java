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

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.Objects;

/**
 * The default pageable implementation.
 *
 * @author graemerocher
 * @since 1.0
 */
@Introspected
final class DefaultPageable implements Pageable {

    private final int max;
    private final int number;
    private final Sort sort;

    /**
     * Default constructor.
     *
     * @param page The page
     * @param size The size
     * @param sort The sort
     */
    @Creator
    DefaultPageable(int page, int size, @Nullable Sort sort) {
        if (page < 0) {
            throw new IllegalArgumentException("Page index cannot be negative");
        }
        if (size == 0) {
            throw new IllegalArgumentException("Size cannot be 0");
        }
        this.max = size;
        this.number = page;
        this.sort = sort == null ? Sort.unsorted() : sort;
    }

    @Override
    public int getSize() {
        return max;
    }

    @Override
    public int getNumber() {
        return number;
    }

    @NonNull
    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultPageable that)) {
            return false;
        }
        return max == that.max &&
                number == that.number &&
                Objects.equals(sort, that.sort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(max, number, sort);
    }

    @Override
    public String toString() {
        return "DefaultPageable{" +
                "max=" + max +
                ", number=" + number +
                ", sort=" + sort +
                '}';
    }
}
