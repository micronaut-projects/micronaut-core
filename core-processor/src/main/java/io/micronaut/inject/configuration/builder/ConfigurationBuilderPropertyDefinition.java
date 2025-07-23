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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

/**
 * Configuration builder method.
 *
 * @param name      The property name
 * @param method    The method
 * @param parameter The parameter
 * @param path      The property path
 * @author Denis Stepanov
 * @see io.micronaut.context.annotation.ConfigurationBuilder
 * @since 4.10
 */
@Internal
public record ConfigurationBuilderPropertyDefinition(String name,
                                                     MethodElement method,
                                                     ClassElement type,
                                                     @Nullable ParameterElement parameter,
                                                     String path) {
}
