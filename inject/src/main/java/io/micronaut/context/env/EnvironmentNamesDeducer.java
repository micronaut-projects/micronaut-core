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

import java.util.Set;

/**
 * A strategy interface for deducing environment names. Implementations of this interface
 * determine the set of environment names based on specific criteria or configurations.
 *
 * This interface is useful for frameworks and applications that need to dynamically
 * identify or configure themselves based on the current runtime environment.
 *
 * The default implementation, {@code NONE}, provides an empty set of environment names,
 * effectively disabling any environment deduction.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
public interface EnvironmentNamesDeducer {

    /**
     * A default implementation of {@link EnvironmentNamesDeducer} that provides an empty set
     * of environment names. This effectively disables any environment name deduction.
     *
     * Use this implementation when no environment-specific configuration or deduction is required.
     */
    EnvironmentNamesDeducer NONE = Set::of;

    /**
     * Deduces and returns a set of environment names based on the implemented strategy.
     * The returned set can be used to identify the current runtime environment, e.g.,
     * development, staging, production, etc.
     *
     * @return a non-null set of environment names, which may be empty if no environment
     *         names can be deduced.
     */
    @NonNull
    Set<String> deduceEnvironmentNames();

}
