/*
 * Copyright 2018 original authors
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
package io.micronaut.context;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;

import javax.annotation.Nullable;

/**
 * A field injection point invoked via reflection
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
class ReflectionFieldInjectionPoint<T> extends DefaultFieldInjectionPoint<T> {
    ReflectionFieldInjectionPoint(
            BeanDefinition declaringBean,
            Class declaringType,
            Class<T> fieldType,
            String field,
            @Nullable AnnotationMetadata annotationMetadata,
            @Nullable Argument[] typeArguments) {
        super(declaringBean, declaringType, fieldType, field, annotationMetadata, typeArguments);
    }

    @Override
    public boolean requiresReflection() {
        return true;
    }
}
