/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.core.util.functional;

/**
 * Consumer with a generic exception.
 *
 * @param <T> the type accepted by this consumer
 * @param <E> the type of exception thrown from the supplier
 * @author Jonas Konrad
 * @since 4.8.0
 */
@FunctionalInterface
public interface ThrowingConsumer<T, E extends Throwable> {
    /**
     * Consume the value.
     *
     * @param t The value
     * @throws E The generic exception
     */
    void accept(T t) throws E; // parameter nullability is inherited from TYPE_USE on T
}
