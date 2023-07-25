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
package io.micronaut.core.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * <p>Utility methods for working with objects</p>.
 *
 * @author Denis Stepanov
 * @since 4.0
 */
@Internal
public final class ObjectUtils {

    private ObjectUtils() {
    }

    /**
     * Hashing method. Alternative to {@link java.util.Objects#hash(Object...)} without allocating an array.
     * @param o1 The object 1
     * @param o2 The object 2
     * @return The hash
     * @since 4.0.0
     */
    public static int hash(@Nullable Object o1, @Nullable Object o2) {
        int result = 1;
        result = 31 * result + (o1 == null ? 0 : o1.hashCode());
        result = 31 * result + (o2 == null ? 0 : o2.hashCode());
        return result;
    }

    /**
     * Hashing method. Alternative to {@link Objects#hash(Object...)} without allocating an array.
     * @param o1 The object 1
     * @param o2 The object 2
     * @param o3 The object 3
     * @return The hash
     * @since 4.0.0
     */
    public static  int hash(@Nullable Object o1, @Nullable Object o2, @Nullable Object o3) {
        int result = 1;
        result = 31 * result + (o1 == null ? 0 : o1.hashCode());
        result = 31 * result + (o2 == null ? 0 : o2.hashCode());
        result = 31 * result + (o3 == null ? 0 : o3.hashCode());
        return result;
    }

    /**
     * Coerce the given object to boolean. The following cases are handled:
     *
     * <ol>
     *     <li>{@code null} results in {@code false}</li>
     *     <li>empty strings result in {@code false}</li>
     *     <li>positive numbers are {@code true}</li>
     *     <li>empty collections, arrays, optionals and maps are {@code false}</li>
     * </ol>
     * @param object The object
     * @return The boolean
     * @since 4.0.0
     */
    @SuppressWarnings("unused") // used by expressions
    public static boolean coerceToBoolean(@Nullable Object object) {
        if (object == null) {
            return false;
        } else if (object instanceof Boolean b) {
            return b;
        } else if (object instanceof CharSequence charSequence) {
            return charSequence.length() > 0;
        } else if (object instanceof Number n) {
            return n.doubleValue() != 0;
        } else if (object instanceof Collection<?> col) {
            return !col.isEmpty();
        } else if (object instanceof Map<?, ?> col) {
            return !col.isEmpty();
        } else if (object instanceof Object[] array) {
            return array.length > 0;
        } else if (object instanceof Optional<?> opt) {
            return opt.isPresent();
        }
        return true;

    }
}
