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
package io.micronaut.inject.configuration.builder;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;

/**
 * Represents a common interface for definitions of configuration builder methods.
 * <p>
 * This sealed interface is implemented by specific types of builder method definitions,
 * such as {@link ConfigurationBuilderDurationMethodDefinition} for methods that accept a long and a TimeUnit,
 * and {@link ConfigurationBuilderPropertyDefinition} for general configuration builder methods.
 *
 * @author Denis Stepanov
 * @see ConfigurationBuilderDurationMethodDefinition
 * @see ConfigurationBuilderPropertyDefinition
 * @since 4.10
 */
@Internal
public sealed interface ConfigurationBuilderElementDefinition permits ConfigurationBuilderDurationMethodDefinition, ConfigurationBuilderPropertyDefinition {

    /**
     * @return The name of the property.
     */
    String name();

    /**
     * @return The property path.
     */
    String path();

    /**
     * @return The property type.
     */
    ClassElement type();

}
