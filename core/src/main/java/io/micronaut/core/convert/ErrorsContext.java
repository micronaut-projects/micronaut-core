/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.core.convert;

import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;

/**
 * Interface for reporting conversion errors.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ErrorsContext extends Iterable<ConversionError> {

    /**
     * Reject the version with the given exception.
     *
     * @param exception The exception
     */
    default void reject(Exception exception) {
        // no-op - in some cases conversion errors can simply be ignored
    }

    /**
     * Reject the version with the given exception.
     *
     * @param value     The original value
     * @param exception The exception
     */
    default void reject(Object value, Exception exception) {
        // no-op - in some cases conversion errors can simply be ignored
    }

    @Override
    default Iterator<ConversionError> iterator() {
        return Collections.emptyIterator();
    }

    /**
     * Obtain the last error.
     *
     * @return The error
     */
    default Optional<ConversionError> getLastError() {
        return Optional.empty();
    }

    /**
     * @return Check whether errors exist
     */
    default boolean hasErrors() {
        return getLastError().isPresent();
    }

}
