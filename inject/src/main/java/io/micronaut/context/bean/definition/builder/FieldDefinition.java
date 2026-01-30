/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.context.bean.definition.builder;

import io.micronaut.core.annotation.AnnotationMetadata;

import java.util.List;

/**
 * Describes a field injection in a bean definition.
 *
 * @param <K> The bean element kind type
 * @param <F> The field representation type
 * @author Denis Stepanov
 * @since 5.0
 */
public record FieldDefinition<K, F>(F fieldElement,
                                    AnnotationMetadata annotationMetadata,
                                    BeanDefinitionInjectionPoint<K> injectionPoint,
                                    boolean requiresReflection,
                                    boolean isOptional) implements MemberDefinition<K> {

    @Override
    public List<BeanDefinitionInjectionPoint<K>> injectionPoints() {
        return List.of();
    }
}
