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
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;

import java.util.List;

/**
 * A record implementation of the {@link ConfigurationBuilderDefinition} interface,
 * representing the definition of configuration builder elements based on a field.
 * This class is utilized for associating configuration elements with a field element.
 * <p>
 * It encapsulates a {@link FieldElement} to represent the field and a list of
 * {@link ConfigurationBuilderPropertyDefinition} that define individual configuration
 * elements related to the builder.
 *
 * @param fieldElement The field element used as the basis for the configuration builder.
 * @param elements     The list of configuration builder element definitions associated with the field.
 * @author Denis Stepanov
 * @since 4.10
 */
@Internal
public record ConfigurationBuilderOfFieldDefinition(
    FieldElement fieldElement,
    List<ConfigurationBuilderPropertyDefinition> elements
) implements ConfigurationBuilderDefinition {
    @Override
    public MemberElement builderElement() {
        return fieldElement;
    }

    @Override
    public ClassElement builderType() {
        return fieldElement.getType();
    }
}
