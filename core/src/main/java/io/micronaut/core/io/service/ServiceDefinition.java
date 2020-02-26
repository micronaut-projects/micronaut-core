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
package io.micronaut.core.io.service;

import java.util.function.Supplier;

/**
 * A service that may or may not be present on the classpath.
 *
 * @param <T> The service type
 */
public interface ServiceDefinition<T> {

    /**
     * @return The full class name of the service
     */
    String getName();

    /**
     * @return is the service present
     */
    boolean isPresent();

    /**
     * Load the service of throw the given exception.
     *
     * @param exceptionSupplier The exception supplier
     * @param <X>               The exception type
     * @return The instance
     * @throws X The exception concrete type
     */
    <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X;

    /**
     * @return load the service
     */
    T load();
}
