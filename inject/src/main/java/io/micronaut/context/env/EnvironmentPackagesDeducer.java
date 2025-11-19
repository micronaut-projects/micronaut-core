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
package io.micronaut.context.env;

import io.micronaut.core.annotation.NonNull;

import java.util.List;

/**
 * Defines a contract for deducing a list of package names associated with the current environment.
 * Implementations of this interface determine the relevant package names dynamically at runtime
 * based on the specific environment or context they execute in.
 *
 * The {@link EnvironmentPackagesDeducer#NONE} constant provides an implementation
 * that returns an empty list, effectively representing a no-op deducer.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
public interface EnvironmentPackagesDeducer {

    /**
     * A predefined, constant implementation of {@link EnvironmentPackagesDeducer} that represents
     * a no-operation strategy. The {@code NONE} instance always returns an empty list when the
     * {@link #deducePackages()} method is invoked, signifying that no package names are deduced
     * or applicable for the current environment.
     */
    EnvironmentPackagesDeducer NONE = List::of;

    /**
     * Deduces and returns a list of package names relevant to the current runtime environment.
     * The specific package names are determined dynamically based on the context in which
     * this method is executed.
     *
     * @return a non-null list of strings representing the deduced package names. The list may
     *         be empty if no relevant packages are identified.
     */
    @NonNull
    List<String> deducePackages();

}
