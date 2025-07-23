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
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;

import java.util.List;

/**
 * A record representing a method-based configuration builder definition.
 *
 * @param method   The method
 * @param elements The elements
 */
@Internal
public record ConfigurationBuilderOfMethodDefinition(
    MethodElement method,
    List<ConfigurationBuilderPropertyDefinition> elements
) implements ConfigurationBuilderDefinition {
    @Override
    public MemberElement builderElement() {
        return method;
    }

    @Override
    public ClassElement builderType() {
        return method.getReturnType();
    }
}
