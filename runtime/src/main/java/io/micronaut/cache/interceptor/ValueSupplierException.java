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
package io.micronaut.cache.interceptor;

/**
 * An exception thrown when the Supplier of a cache value causes an exception.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class ValueSupplierException extends RuntimeException {

    private final Object key;

    /**
     * Create a new exception with the key and cause.
     *
     * @param key The key for the given annotated element and parameters
     * @param cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).
     */
    ValueSupplierException(Object key, RuntimeException cause) {
        super(cause);
        this.key = key;
    }

    @Override
    public synchronized RuntimeException getCause() {
        return (RuntimeException) super.getCause();
    }

    /**
     * @return The cache key
     */
    public Object getKey() {
        return key;
    }
}
